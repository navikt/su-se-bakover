package no.nav.su.se.bakover.client.kafka

import no.nav.su.se.bakover.common.ApplicationConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.errors.AuthorizationException
import org.slf4j.LoggerFactory
import java.time.Duration

internal class KafkaPublisherClient(
    private val kafkaConfig: ApplicationConfig.KafkaConfig,
    private val initProducer: () -> Producer<String, String> = { KafkaProducer(kafkaConfig.producerConfig) }
) : KafkaPublisher {
    private val log = LoggerFactory.getLogger(this::class.java)
    private var producer: Producer<String, String> = initProducer()
    private val failed: MutableSet<ProducerRecord<String, String>> = mutableSetOf()

    override fun publiser(topic: String, melding: String) {
        return publiser(topic, melding, 0)
    }

    private fun publiser(topic: String, melding: String, retries: Int) {
        try {
            val record = ProducerRecord<String, String>(topic, melding)
            producer.send(record) { recordMetadata, exception ->
                when (exception) {
                    null -> log.info("Publiserte meldig til $topic med offset: ${recordMetadata.offset()}")
                    is AuthorizationException -> {
                        log.warn("Autorisasjonsfeil ved publisering av melding til $topic, rekonfigurerer producer før retry.")
                        producer.close(Duration.ZERO)
                        producer = initProducer()
                        // TODO solution for the record that failed
                        failed.add(record)
                    }
                    else -> log.error("Ukejent feil ved publisering av melding til topic:$topic", exception)
                }
            }
        } catch (exception: Throwable) {
            log.error("Fanget exception ved publisering av melding til topic:$topic", exception)
        }
    }
}
