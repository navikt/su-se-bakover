package no.nav.su.se.bakover.common.domain.kafka

interface KafkaPublisher {
    fun publiser(topic: String, melding: String)
}
