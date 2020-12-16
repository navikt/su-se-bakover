package no.nav.su.se.bakover.client.statistikk

import no.nav.su.se.bakover.common.Config

interface StatistikkProducer {
    fun publiser(topic: Config.Kafka.StatistikkTopic, melding: String)
}
