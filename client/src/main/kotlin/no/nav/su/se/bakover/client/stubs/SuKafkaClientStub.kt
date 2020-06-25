package no.nav.su.se.bakover.client.stubs

import no.nav.su.se.bakover.client.KafkaMessage
import no.nav.su.se.bakover.client.SuKafkaClient
import org.slf4j.LoggerFactory

object SuKafkaClientStub : SuKafkaClient {
    private val logger = LoggerFactory.getLogger(SuKafkaClientStub::class.java)
    val sentRecords = mutableListOf<KafkaMessage>()
    override fun send(message: KafkaMessage) {
        logger.info("Sending record:$message")
        sentRecords.add(message)
    }
}
