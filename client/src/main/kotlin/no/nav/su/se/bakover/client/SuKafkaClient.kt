package no.nav.su.se.bakover.client

import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory

interface SuKafkaClient {
    fun send(message: KafkaMessage)
}

data class KafkaMessage(
    val topic: String,
    val key: String,
    val value: String
)

class SuKafkaClientImpl(
    private val producer: Producer<String, String>
) : SuKafkaClient {
    private val logger = LoggerFactory.getLogger(SuKafkaClientImpl::class.java)
    override fun send(message: KafkaMessage) {
        try {
            producer.send(ProducerRecord(message.topic, message.key, message.value)) { metadata, exception ->
                exception?.let { logger.error("Exception while sending message to topic:${message.topic}, key:${message.key}, exception:$it") }
                logger.info("Successfully sent message to topic:${message.topic}, key:${message.key}, offset:${metadata.offset()}")
            }
        } catch (exeption: Throwable) {
            logger.error("Exception caught while sending message to topic:${message.topic}, key:${message.key}, exception:$exeption")
        }
    }
}
