package no.nav.su.se.bakover.client.kafka

interface KafkaPublisher {
    fun publiser(topic: String, melding: String)
}
