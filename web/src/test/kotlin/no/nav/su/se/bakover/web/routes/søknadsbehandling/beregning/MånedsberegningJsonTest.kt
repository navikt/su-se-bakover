package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.beregning.MånedsberegningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.test.månedsperiodeJanuar2020
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class MånedsberegningJsonTest {
    companion object {
        //language=JSON
        internal const val expectedMånedsberegningJson =
            """
            {
                "fraOgMed":"2020-01-01",
                "tilOgMed":"2020-01-31",
                "sats":"HØY",
                "grunnbeløp":99858,
                "beløp":20637,
                "fradrag": [],
                "satsbeløp": 20637,
                "epsFribeløp": 100,
                "epsInputFradrag": [],
                "merknader": []
            }
            """

        internal val månedsberegning = MånedsberegningFactory.ny(
            måned = månedsperiodeJanuar2020,
            sats = Sats.HØY,
            fradrag = emptyList(),
        )
    }

    @Test
    fun json() {
        JSONAssert.assertEquals(
            expectedMånedsberegningJson.trimIndent(),
            serialize(månedsberegning.toJson(100.0, emptyList())),
            true
        )
    }
}
