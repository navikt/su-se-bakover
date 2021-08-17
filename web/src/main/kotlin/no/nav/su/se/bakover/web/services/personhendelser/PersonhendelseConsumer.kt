package no.nav.su.se.bakover.web.services.personhendelser

import arrow.core.Either
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.service.hendelser.PersonhendelseService
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.concurrent.timer
import no.nav.person.pdl.leesah.Personhendelse as EksternPersonhendelse

class PersonhendelseConsumer(
    private val consumer: Consumer<String, EksternPersonhendelse>,
    private val personhendelseService: PersonhendelseService,
    periode: Long = Duration.ofSeconds(120L).toMillis(),
    initialDelay: Long = 0,
    topicName: String = "aapen-person-pdl-leesah-v1",
    private val pollTimeoutDuration: Duration = Duration.ofMillis(5000),
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    init {
        log.info("Setter opp Kafka-Consumer som lytter på $topicName fra PDL")
        consumer.subscribe(listOf(topicName))

        timer(
            name = "PersonhendelseConsumer",
            daemon = true,
            period = periode,
            initialDelay = initialDelay,
        ) {
            Either.catch {
                consume()
            }.mapLeft {
                // Dette vil føre til en timeout, siden vi ikke gjør noen commit. Da vil vi ikke få noen meldinger i mellomtiden.
                log.error("Ukjent feil ved konsumering av personhendelser.", it)
            }
        }
    }

    private fun consume() {
        val messages = consumer.poll(pollTimeoutDuration)
        val processedMessages = mutableMapOf<TopicPartition, OffsetAndMetadata>()

        if (!messages.isEmpty) {
            run processMessages@{
                messages.forEach { message ->
                    HendelseMapper.map(message).fold(
                        ifLeft = {
                            when (it) {
                                HendelseMapperException.IkkeAktuellOpplysningstype -> {
                                    // TODO jah: Flytt denne logikken til service/domenelaget
                                    // Vi ønsker ikke få disse melding sendt på nytt.
                                    processedMessages[TopicPartition(message.topic(), message.partition())] =
                                        OffsetAndMetadata(message.offset() + 1)
                                }
                                HendelseMapperException.KunneIkkeHenteAktørId -> {
                                    if (ApplicationConfig.isNotProd()) {
                                        log.warn("Feil skjedde ved henting av aktørId for melding med offset: ${message.offset()}. Vi ignorerer disse meldingene i preprod.")
                                        processedMessages[TopicPartition(message.topic(), message.partition())] =
                                            OffsetAndMetadata(message.offset() + 1)
                                    } else {
                                        log.error("Feil skjedde ved henting av aktørId for melding med offset: ${message.offset()}")
                                        consumer.commitSync(processedMessages)
                                        // Kafka tar ikke hensyn til offsetten vi comitter før det skjer en Rebalance.
                                        // Vi kan tvinge en rebalance eller gjøre en seek, dersom vi ikke ønsker neste event (som kan føre til at vi comitter lengre frem enn vi faktisk er)
                                        // Andre løsninger kan være å bruke en dead-letter topic eller lagre hendelsene rått til basen.
                                        consumer.enforceRebalance()
                                        return@processMessages
                                    }
                                }
                            }
                        },
                        ifRight = {
                            personhendelseService.prosesserNyHendelse(it)
                            processedMessages[TopicPartition(message.topic(), message.partition())] =
                                OffsetAndMetadata(it.offset + 1)
                        },
                    )
                }
                // I tilfeller der vi har prosessert alle meldingene OK.
                consumer.commitSync(processedMessages)
            }
        }
    }
}
