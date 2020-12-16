package no.nav.su.se.bakover.service.statistikk

import com.worldturner.medeia.api.UrlSchemaSource
import com.worldturner.medeia.api.jackson.MedeiaJacksonApi
import no.nav.su.se.bakover.client.statistikk.SakSchema
import no.nav.su.se.bakover.client.statistikk.StatistikkProducer
import no.nav.su.se.bakover.common.Config
import no.nav.su.se.bakover.common.objectMapper
import java.io.StringWriter

class StatistikkServiceImpl(val producer: StatistikkProducer): GG {
    private val api = MedeiaJacksonApi()

    private fun validerOgLagMelding(sakSchema: SakSchema): String {
        val validator = api.loadSchema(UrlSchemaSource(this::class.java.getResource("/sak_schema.json")))
        val s = StringWriter()
        val unvalidatedGenerator = objectMapper.factory.createGenerator(s)
        val validatedGenerator = api.decorateJsonGenerator(validator, unvalidatedGenerator)

        objectMapper.writeValue(validatedGenerator, sakSchema)
        return objectMapper.writeValueAsString(sakSchema)
    }

    fun publiser(sakSchema: SakSchema) {
        val melding = validerOgLagMelding(sakSchema)
        publiser(Config.Kafka.StatistikkTopic.Sak, melding)
    }

    private fun publiser(topic: Config.Kafka.StatistikkTopic, melding: String) {
        TODO()
    }
}
