package no.nav.su.se.bakover.web.services.PdlHendelser

import no.nav.person.pdl.leesah.Personhendelse
import no.nav.su.se.bakover.service.hendelser.PersonhendelseService
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.concurrent.timer

class LeesahConsumer(
    private val consumer: Consumer<String, Personhendelse>,
    private val personhendelseService: PersonhendelseService,
) {
    private val topicName = "aapen-person-pdl-leesah-v1"
    private val log = LoggerFactory.getLogger(this::class.java)
    private val POLL_TIMEOUT_DURATION = Duration.ofMillis(5000)

    init {
        log.info("Setter opp Kafka-Consumer som lytter på $topicName fra PDL")
        consumer.subscribe(listOf(topicName))

        timer(
            name = "LeesahMqConsumer",
            daemon = true,
            period = Duration.ofSeconds(120L).toMillis(),
        ) {
            consume()
        }
    }

    private fun consume() {
        val messages = consumer.poll(POLL_TIMEOUT_DURATION)
        val processedMessages = mutableMapOf<TopicPartition, OffsetAndMetadata>()

        if (!messages.isEmpty) {
            run processMessages@{
                messages.forEach { message ->
                    HendelseMapper.map(message).fold(
                        ifLeft = {
                            when (it) {
                                HendelseMapperException.IkkeAktuellOpplysningstype -> {}
                                HendelseMapperException.KunneIkkeHenteAktørId -> {
                                    log.error("Feil skjedde ved henting av aktørId for melding med offset: ${message.offset()}")
                                    // TODO ai: Muligt å hoppe over meldinger i Q som feiler men ikke i prod
                                    return@processMessages
                                }
                            }
                        },
                        ifRight = {
                            personhendelseService.prosesserNyMelding(it)
                            processedMessages[TopicPartition(message.topic(), message.partition())] =
                                OffsetAndMetadata(it.offset + 1)
                        },
                    )
                }
            }
            consumer.commitSync(processedMessages)
        }
    }
}
