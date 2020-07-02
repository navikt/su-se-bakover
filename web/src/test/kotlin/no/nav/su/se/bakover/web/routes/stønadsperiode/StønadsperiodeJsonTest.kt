package no.nav.su.se.bakover.web.routes.stønadsperiode

import io.kotest.assertions.json.shouldMatchJson
import io.kotest.matchers.shouldBe
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.Stønadsperiode
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.routes.søknad.SøknadJsonTest.Companion.søknadJsonString
import no.nav.su.se.bakover.web.serialize
import org.junit.jupiter.api.Test

internal class StønadsperiodeJsonTest {

    //language=JSON
    val stønadsperiodeJsonString = """
            {
                "id": 1,
                "søknad": $søknadJsonString,
                "behandlinger": []
            }
        """.trimIndent()

    val stønadsperiode = Stønadsperiode(
        id = 1,
        søknad = Søknad(
            id = 1,
            søknadInnhold = SøknadInnholdTestdataBuilder.build()
        ),
        behandlinger = mutableListOf()
    )

    @Test
    fun `should serialize to json string`() {
        serialize(stønadsperiode.toDto().toJson()) shouldMatchJson stønadsperiodeJsonString
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<StønadsperiodeJson>(stønadsperiodeJsonString) shouldBe stønadsperiode.toDto().toJson()
    }
}
