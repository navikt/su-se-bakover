package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.beregning.MånedsberegningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.util.UUID

internal class MånedsberegningJsonTest {
    companion object {
        private val uuid = UUID.randomUUID()

        //language=JSON
        internal val expectedMånedsberegningJson =
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
                "epsInputFradrag": []
            }
        """

        internal val månedsberegning = MånedsberegningFactory.ny(
            periode = Periode.create(1.januar(2020), 31.januar(2020)),
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
