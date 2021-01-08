package no.nav.su.se.bakover.client.kafka

import no.nav.su.se.bakover.common.ApplicationConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.AuthorizationException
import org.slf4j.LoggerFactory
import java.time.Duration

internal class KafkaPublisherClient(
    private val kafkaConfig: ApplicationConfig.KafkaConfig
) : KafkaPublisher {
    private val log = LoggerFactory.getLogger(this::class.java)
    private var producer = KafkaProducer<String, String>(kafkaConfig.producerConfig)

    override fun publiser(topic: String, melding: String) {
        publiser(topic, melding, 0)
    }

    private fun publiser(topic: String, melding: String, retries: Int) {
        try {
            producer.send(ProducerRecord(topic, melding)) { recordMetadata, exception ->
                when (exception) {
                    null -> log.info("Publiserte meldig til $topic med offset: ${recordMetadata.offset()}")
                    is AuthorizationException -> {
                        log.warn("Autorisasjonsfeil ved publisering av melding til $topic, rekonfigurerer producer før retry.")
                        producer.close(Duration.ZERO)
                        producer = KafkaProducer<String, String>(kafkaConfig.producerConfig).also {
                            if (retries < 3) {
                                log.info("Resender melding til $topic med ny producer, retry antall: $retries")
                                Thread.sleep(500)
                                publiser(topic, melding, retries + 1)
                            } else {
                                log.info("Antall retry overskredet, stopper.")
                            }
                        }
                    }
                    else -> log.error("Ukejent feil ved publisering av melding til topic:$topic", exception)
                }
            }
        } catch (exception: Throwable) {
            log.error("Fanget exception ved publisering av melding til topic:$topic", exception)
        }
    }
}
