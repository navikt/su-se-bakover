package no.nav.su.se.bakover.web.services.personhendelser

import arrow.core.Either
import no.nav.su.se.bakover.common.ApplicationConfig
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.domain.hendelse.Personhendelse
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
                                is KunneIkkeMappePersonhendelse.IkkeAktuellOpplysningstype -> {
                                    // TODO jah: Flytt denne logikken til service/domenelaget
                                    // Vi ønsker ikke få disse hendelsene sendt på nytt.
                                    processedMessages[TopicPartition(message.topic(), message.partition())] =
                                        OffsetAndMetadata(message.offset() + 1)
                                    log.debug("Personhendelse: Uaktuell personhendelse: Ignorerer hendelse ${it.opplysningstype} med hendelsesid ${it.hendelseId}, offset ${message.offset()}, partisjon ${message.partition()}")
                                }
                                is KunneIkkeMappePersonhendelse.KunneIkkeHenteAktørId -> {
                                    if (ApplicationConfig.isNotProd()) {
                                        log.warn("Personhendelse: Feil skjedde ved henting av aktørId for hendelse ${it.opplysningstype} med hendelsesid ${it.hendelseId}, offset ${message.offset()}, partisjon ${message.partition()}. Vi ignorerer disse hendelsene i preprod.")
                                        sikkerLogg.warn("Personhendelse: Feil skjedde ved henting av aktørId for key=${message.key()}, value=${message.value()}, offset ${message.offset()}, partisjon ${message.partition()}. Vi ignorerer disse hendelsene i preprod.")
                                        processedMessages[TopicPartition(message.topic(), message.partition())] =
                                            OffsetAndMetadata(message.offset() + 1)
                                    } else {
                                        log.error("Personhendelse: Feil skjedde ved henting av aktørId for hendelse ${it.opplysningstype} med hendelsesid ${it.hendelseId}, offset ${message.offset()}, partisjon ${message.partition()}.")
                                        sikkerLogg.error("Personhendelse: Feil skjedde ved henting av aktørId for key=${message.key()}, value=${message.value()}, offset ${message.offset()}, partisjon ${message.partition()}.")
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
                            if (hendelse is Personhendelse.Hendelse.UtflyttingFraNorge && hendelse.utflyttingsdato == null) {
                                // TODO jah: Finn ut hvorfor disse ikke kommer med når vi legger inn datoen i Dolly.
                                log.info("Personhendelse: Mottok en utflytting fra norge hendelse ${it.metadata.hendelseId} uten utflyttingsdato. Se sikkerlogg for mer informasjon.")
                                sikkerLogg.info("Personhendelse: Mottok en utflytting fra norge hendelse key=${message.key()}, value=${message.value()}, offset ${message.offset()}, partisjon ${message.partition()}.")
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
        }
    }
}
