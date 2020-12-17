package no.nav.su.se.bakover.service.statistikk

import com.worldturner.medeia.api.jackson.MedeiaJacksonApi
import no.nav.su.se.bakover.client.kafka.KafkaPublisher
import no.nav.su.se.bakover.common.Config

internal class StatistikkServiceImpl(
    private val publisher: KafkaPublisher
) : StatistikkService {
    private val api = MedeiaJacksonApi()

    override fun publiser(statistikk: Statistikk) {
        when (statistikk) {
            is Statistikk.Sak -> publisher.publiser(Config.Kafka.StatistikkTopic.Sak.name, "")
        }
    }
}
