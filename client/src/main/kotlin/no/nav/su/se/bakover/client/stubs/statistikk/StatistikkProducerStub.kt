package no.nav.su.se.bakover.client.stubs.statistikk

import no.nav.su.se.bakover.client.statistikk.StatistikkProducer
import no.nav.su.se.bakover.common.Config
import org.slf4j.LoggerFactory

object StatistikkProducerStub : StatistikkProducer {
    private val log = LoggerFactory.getLogger(this::class.java)
    override fun publiser(topic: Config.Kafka.StatistikkTopic, melding: String) {
        log.info("Publiserte melding til topic: ${topic.name}, melding:$melding")
    }
}
