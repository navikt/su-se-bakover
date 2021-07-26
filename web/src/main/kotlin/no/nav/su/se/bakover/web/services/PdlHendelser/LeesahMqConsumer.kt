package no.nav.su.se.bakover.web.services.PdlHendelser

import no.nav.person.pdl.leesah.Personhendelse
import org.apache.kafka.clients.consumer.Consumer
import org.slf4j.LoggerFactory
import java.time.Duration

class LeesahMqConsumer(
    private val topicName: String,
    private val consumer: Consumer<String, Personhendelse>,
    private val leesahService: LeesahService,
) {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val POLL_TIMEOUT_DURATION = Duration.ofMillis(5000)

    init {
        log.info("Setter opp Kafka-Consumer som lytter på $topicName fra PDL")
        consumer.subscribe(listOf(topicName))
    }

    internal fun consume() {
        val messages = consumer.poll(POLL_TIMEOUT_DURATION)

        if (!messages.isEmpty) {
            messages.forEach { message ->
                println("offset: ${message.offset()}, key: ${message.key()}, value: ${message.value()}")

                val record = message.value()
                try {
                    leesahService.prosesserNyMelding(
                        PdlHendelse(
                            hendelseId = record.getHendelseId(),
                            gjeldendeAktørId = null, // TODO
                            offset = message.offset(),
                            opplysningstype = record.getOpplysningstype(),
                            endringstype = record.getEndringstype().toString(),
                            personIdenter = record.getPersonidenter(),
                            dødsdato = record.getDoedsfall().getDoedsdato(),
                            fødselsdato = null,
                            fødeland = null,
                            utflyttingsdato = record.getUtflyttingFraNorge().getUtflyttingsdato())
                    )
                } catch (ex: Exception) {
                    log.error("Feil skjedde ved prossesering av hendelse fra PDL.", ex)
                }
                consumer.commitSync() // TODO ai: commit async eller sync?
            }
        }
    }
}
