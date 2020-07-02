package no.nav.su.se.bakover.web.routes.sak

import io.kotest.assertions.json.shouldMatchJson
import io.kotest.matchers.shouldBe
import no.nav.su.meldinger.kafka.soknad.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Stønadsperiode
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.web.deserialize
import no.nav.su.se.bakover.web.routes.søknad.SøknadJsonTest
import no.nav.su.se.bakover.web.serialize
import org.junit.jupiter.api.Test

internal class SakJsonTest {

    //language=JSON
    val sakJsonString = """
            {
                "id": 1,
                "fnr": "12345678910",
                "stønadsperioder": [
                  {
                    "id": 1,
                    "søknad": ${SøknadJsonTest.søknadJsonString},
                    "behandlinger": []
                  }
                ]
            }
        """.trimIndent()

    val sak = Sak(
        id = 1,
        fnr = Fnr("12345678910"),
        stønadsperioder = mutableListOf(
            Stønadsperiode(
                id = 1,
                søknad = Søknad(
                    id = 1,
                    søknadInnhold = SøknadInnholdTestdataBuilder.build()
                ),
                behandlinger = mutableListOf()
            )
        )
    )

    @Test
    fun `should serialize to json string`() {
        serialize(sak.toDto().toJson()) shouldMatchJson sakJsonString
    }

    @Test
    fun `should deserialize json string`() {
        deserialize<SakJson>(sakJsonString) shouldBe sak.toDto().toJson()
    }
}
