package no.nav.su.se.bakover.client.stubs.kafka

import no.nav.su.se.bakover.common.domain.kafka.KafkaPublisher
import org.slf4j.LoggerFactory

data object KafkaPublisherStub : KafkaPublisher {
    private val log = LoggerFactory.getLogger(this::class.java)
    override fun publiser(topic: String, melding: String) {
        log.info("Publiserte melding til topic: $topic, melding:$melding")
    }
}
