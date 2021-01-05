package no.nav.su.se.bakover.client.kafka

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

internal class KafkaPublisherClient(
    private val producer: KafkaProducer<String, String>
) : KafkaPublisher {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun publiser(topic: String, melding: String) {
        try {
            producer.send(ProducerRecord(topic, melding)) { recordMetadata, exception ->
                if (exception != null)
                    log.error("Feil ved publisering av melding til topic:$topic", exception)
                else log.info("Publiserte meldig til $topic med offset: ${recordMetadata.offset()}")
            }
        } catch (exception: Throwable) {
            log.error("Fanget exception ved publisering av melding til topic:$topic", exception)
        }
    }
}
