package no.nav.su.se.bakover.web.services.personhendelser

import arrow.core.Either
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.CorrelationId.Companion.withCorrelationId
import no.nav.su.se.bakover.domain.personhendelse.Personhendelse
import no.nav.su.se.bakover.service.personhendelser.PersonhendelseService
import org.apache.kafka.clients.consumer.Consumer
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
                    withCorrelationId {
                        val messages = consumer.poll(pollTimeoutDuration)
                        if (!messages.isEmpty) {
                            consume(messages)
                        }
                    }
                }.mapLeft {
                    // Dette vil føre til en timeout, siden vi ikke gjør noen commit. Da vil vi ikke få noen meldinger i mellomtiden.
                    log.error("Personhendelse: Ukjent feil ved konsumering av personhendelser. Utfører en rebalance (melder oss ut) og venter 60 sekunder før vi melder oss inn og prøver igjen. Se stack-trace for mer informasjon.", it)
                    consumer.enforceRebalance()
                    delay(60.seconds)
                }
            }
        }
    }

    private fun consume(messages: ConsumerRecords<String, EksternPersonhendelse>) {
        val processedMessages = mutableMapOf<TopicPartition, OffsetAndMetadata>()

        log.debug(
            "Personhendelse: ${messages.count()} nye meldinger fra PDL. Første melding er fra ${
            messages.first().value().getOpprettet()
            }",
        )
        run processMessages@{
            messages.forEach { message ->
                val key = message.key().removeUnicodeNullcharacter()
                PersonhendelseMapper.map(message).fold(
                    ifLeft = {
                        when (it) {
                            is KunneIkkeMappePersonhendelse.IkkeAktuellOpplysningstype -> {
                                // TODO jah: Flytt denne logikken til service/domenelaget
                                // Vi ønsker ikke få disse hendelsene sendt på nytt.
                                processedMessages[TopicPartition(message.topic(), message.partition())] =
                                    OffsetAndMetadata(message.offset() + 1)
                            }
                            is KunneIkkeMappePersonhendelse.KunneIkkeHenteAktørId -> {
                                if (ApplicationConfig.isNotProd()) {
                                    log.warn("Personhendelse: Feil skjedde ved henting av aktørId for hendelse ${it.opplysningstype} med hendelsesid ${it.hendelseId}, offset ${message.offset()}, partisjon ${message.partition()}. Vi ignorerer disse hendelsene i preprod.")
                                    sikkerLogg.warn("Personhendelse: Feil skjedde ved henting av aktørId for key=$key, value=${message.value()}, offset ${message.offset()}, partisjon ${message.partition()}. Vi ignorerer disse hendelsene i preprod.")
                                    processedMessages[TopicPartition(message.topic(), message.partition())] =
                                        OffsetAndMetadata(message.offset() + 1)
                                } else {
                                    log.error("Personhendelse: Feil skjedde ved henting av aktørId for hendelse ${it.opplysningstype} med hendelsesid ${it.hendelseId}, offset ${message.offset()}, partisjon ${message.partition()}.")
                                    sikkerLogg.error("Personhendelse: Feil skjedde ved henting av aktørId for key=$key, value=${message.value()}, offset ${message.offset()}, partisjon ${message.partition()}.")
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
                        val hendelse = it.hendelse
                        if (hendelse is Personhendelse.Hendelse.UtflyttingFraNorge && hendelse.utflyttingsdato == null && it.endringstype != Personhendelse.Endringstype.ANNULLERT) {
                            // Har observert flere annulerte utflyttingshendelser som ikke har utflyttingsdato i produksjon.
                            // TODO jah: Finn ut hvorfor disse ikke kommer med når vi legger inn datoen i Dolly.
                            log.info("Personhendelse: Mottok en utflytting fra norge hendelse ${it.metadata.hendelseId} uten utflyttingsdato. Se sikkerlogg for mer informasjon.")
                            sikkerLogg.info("Personhendelse: Mottok en utflytting fra norge hendelse key=$key, value=${message.value()}, offset ${message.offset()}, partisjon ${message.partition()}.")
                        }
                        personhendelseService.prosesserNyHendelse(it)
                        processedMessages[TopicPartition(message.topic(), message.partition())] =
                            OffsetAndMetadata(message.offset() + 1)
                    },
                )
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
}
