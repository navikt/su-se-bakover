package no.nav.su.se.bakover.web.services.personhendelser

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.CorrelationId.Companion.withCorrelationIdSuspend
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseService
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.time.Duration.Companion.seconds
import no.nav.person.pdl.leesah.Personhendelse as EksternPersonhendelse

class PersonhendelseConsumer(
    private val consumer: Consumer<String, EksternPersonhendelse>,
    private val personhendelseService: PersonhendelseService,
    topicName: String = "pdl.leesah-v1",
    // Vi ønsker ikke holde tråden i live for lenge ved en avslutting av applikasjonen.
    private val pollTimeoutDuration: Duration = Duration.ofMillis(500),
    private val log: Logger = LoggerFactory.getLogger(PersonhendelseConsumer::class.java),
    private val sikkerLogg: Logger = no.nav.su.se.bakover.common.sikkerLogg,
) {

    init {
        log.info("Personhendelse: Setter opp Kafka-Consumer som lytter på $topicName fra PDL")
        consumer.subscribe(listOf(topicName))

        CoroutineScope(Dispatchers.IO).launch {
            while (this.isActive) {
                Either.catch {
                    withCorrelationIdSuspend {
                        val messages = consumer.poll(pollTimeoutDuration)
                        if (!messages.isEmpty) {
                            consume(messages)
                        }
                    }
                }.mapLeft {
                    // Dette vil føre til en timeout, siden vi ikke gjør noen commit. Da vil vi ikke få noen meldinger i mellomtiden.
                    log.error(
                        "Personhendelse: Ukjent feil ved konsumering av personhendelser. Utfører en rebalance (melder oss ut) og venter 60 sekunder før vi melder oss inn og prøver igjen. Se stack-trace for mer informasjon.",
                        it,
                    )
                    consumer.enforceRebalance()
                    delay(60.seconds)
                }
            }
        }
    }

    private suspend fun consume(messages: ConsumerRecords<String, EksternPersonhendelse>) {
        val processedMessages = mutableMapOf<TopicPartition, OffsetAndMetadata>()
        log.debug(
            "Personhendelse: ${messages.count()} nye meldinger fra PDL. Første melding er fra ${
                messages.first().value().getOpprettet()
            }",
        )
        run processMessages@{
            messages.forEach { message ->
                prosesserMelding(message).onRight {
                    processedMessages[TopicPartition(message.topic(), message.partition())] =
                        OffsetAndMetadata(message.offset() + 1)
                }.onLeft {
                    // Comitter de tidligere meldingene som har prosessert OK
                    consumer.commitSync(processedMessages)
                    // Kafka tar ikke hensyn til offsetten vi comitter før det skjer en Rebalance.
                    // Vi kan tvinge en rebalance eller gjøre en seek, dersom vi ikke ønsker neste event (som kan føre til at vi comitter lengre frem enn vi faktisk er)
                    consumer.enforceRebalance()
                    delay(60.seconds)
                }
            }
            // I tilfeller der vi har prosessert alle meldingene OK.
            consumer.commitSync(processedMessages)
        }
        log.debug(
            "Personhendelse: Prosessert ferdig meldingene. Siste var til og med: ${
                messages.last().value().opprettet
            })",
        )
    }

    private fun prosesserMelding(
        message: ConsumerRecord<String, EksternPersonhendelse>,
    ): Either<Unit, Unit> {
        return Either.catch {
            return PersonhendelseMapper.map(message).fold(
                ifLeft = {
                    when (it) {
                        is KunneIkkeMappePersonhendelse.IkkeAktuellOpplysningstype -> {
                            // TODO jah: Flytt denne logikken til service/domenelaget
                            // Vi ønsker ikke få disse hendelsene sendt på nytt.
                            Unit.right()
                        }
                    }
                },
                ifRight = {
                    personhendelseService.prosesserNyHendelse(it)
                    Unit.right()
                },
            )
        }.mapLeft {
            log.error(
                "Personhendelse: Ukjent feil ved konsumering av personhendelse for partisjon ${message.partition()} and offset ${message.offset()}. Se sikkerlogg for meldingsdata.",
                it,
            )
            sikkerLogg.error("Personhendelse: Ukjent feil ved konsumering av personhendelse. Message: $message", it)
            Unit.left()
        }
    }
}
