package no.nav.su.se.bakover.web.services.PdlHendelser

import no.nav.person.pdl.leesah.Personhendelse
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.sikkerLogg
import no.nav.su.se.bakover.service.hendelser.PersonhendelseService
import org.apache.kafka.clients.consumer.Consumer
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.concurrent.timer

class LeesahConsumer(
    private val consumer: Consumer<String, Personhendelse>,
    private val personhendelseService: PersonhendelseService
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
            try {
                consume()
            } catch(e: Exception) {
                log.error("Feil skjedde ved konsumering av melding fra leesah")
            }
        }
    }

    fun consume() {
        val messages = consumer.poll(POLL_TIMEOUT_DURATION)

        if (!messages.isEmpty) {
            messages.forEach { message ->
                val record = message.value()

                HendelseMapper.map(
                    key = message.key(),
                    offset = message.offset(),
                    personhendelse = record,
                ).fold(
                    ifLeft = {
                        when (it) {
                            HendelseMapperException.IkkeAktuellOpplysningstype -> {}
                            HendelseMapperException.KunneIkkeHenteAktørId -> {
                                log.error("Feil skjedde ved henting av aktørId. Se sikkerlog for mer info.")
                                sikkerLogg.info(objectMapper.writeValueAsString(record))
                            }
                        }
                    },
                    ifRight = {
                        val meldingJson = objectMapper.writeValueAsString(message)
                        personhendelseService.prosesserNyMelding(it, meldingJson)
                    }
                )
            }
            //consumer.commitSync()
        }
    }
}
