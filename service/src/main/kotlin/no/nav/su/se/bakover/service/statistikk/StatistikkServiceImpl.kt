package no.nav.su.se.bakover.service.statistikk

import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.common.objectMapper
import org.slf4j.LoggerFactory

internal class StatistikkServiceImpl(
    private val publisher: KafkaPublisher
) : StatistikkService {
    private val log = LoggerFactory.getLogger(this::class.java)
    private val schemaValidator = StatistikkSchemaValidator

    override fun publiser(statistikk: Statistikk) {
        val json = objectMapper.writeValueAsString(statistikk)
        val isValid = when (statistikk) {
            is Statistikk.Sak -> schemaValidator.validerSak(json)
        }
        if (isValid) {
            publisher.publiser(
                topic = Config.Kafka.StatistikkTopic.Sak.name,
                melding = json
            )
        } else {
            log.error("Statistikk-objekt validerer ikke mot json-schema!")
        }
    }
}
