package no.nav.su.se.bakover.client.kafka.PdlHendelser

import no.nav.person.pdl.leesah.Personhendelse
import no.nav.su.se.bakover.common.sikkerLogg
import org.apache.kafka.clients.consumer.Consumer
import org.slf4j.LoggerFactory
import java.time.Duration
import kotlin.concurrent.timer

class LeesahMqConsumerImpl(
    private val topicName: String,
    private val consumer: Consumer<String, Personhendelse>,
) : LeesahMqConsumer {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val POLL_TIMEOUT_DURATION = Duration.ofMillis(5000)

    init {
        log.info("Setter opp Kafka-Consumer som lytter på $topicName fra PDL")
        consumer.subscribe(listOf(topicName))

        timer(
            name = "LeesahMqConsumer",
            daemon = true,
            period = Duration.ofSeconds(120L).toMillis()
        ) {
            consume()
        }
    }

    override fun consume() {
        val messages = consumer.poll(POLL_TIMEOUT_DURATION)

        if (!messages.isEmpty) {
            var i = 0
            messages.forEach { message ->
                val record = message.value()
                if (i == 0) {
                    sikkerLogg.info("offset: ${message.offset()}, key: ${message.key()}, value: $record")
                }
                i = (i + 1) % 1000

                    /*
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
                    */
            }
            // consumer.commitSync()
        }
    }
}
