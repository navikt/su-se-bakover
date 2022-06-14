package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.MånedsberegningFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragForMåned
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
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
                "fradrag": [
                  {
                    "type": "ForventetInntekt",
                    "beskrivelse": null,
                    "beløp": 0,
                    "periode": {
                      "fraOgMed": "2020-01-01",
                      "tilOgMed": "2020-01-31"
                    },
                    "utenlandskInntekt": null,
                    "tilhører": "BRUKER"
                  }
                ],
                "satsbeløp": 20637,
                "epsFribeløp": 100,
                "epsInputFradrag": [],
                "merknader": []
            }
            """

        internal val månedsberegning = MånedsberegningFactory.ny(
            måned = januar(2020),
            strategy = BeregningStrategy.BorAlene(satsFactoryTestPåDato()),
            fradrag = listOf(
                FradragForMåned(
                    fradragstype = Fradragstype.ForventetInntekt,
                    månedsbeløp = 0.0,
                    måned = januar(2020),
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )
    }

    @Test
    fun json() {
        JSONAssert.assertEquals(
            expectedMånedsberegningJson.trimIndent(),
            serialize(månedsberegning.toJson(100.0, emptyList())),
            true,
        )
    }
}
