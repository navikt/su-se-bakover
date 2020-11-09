package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.domain.beregning.BeregningFactory
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDateTime
import java.time.Month
import java.util.UUID

internal class BeregningJsonTest {
    companion object {
        private val uuid = UUID.randomUUID()

        //language=JSON
        internal val expectedBeregningJson =
            """
            {
                "id":"$uuid",
                "opprettet":"2020-08-01T12:15:15Z",
                "fraOgMed":"2020-08-01",
                "tilOgMed":"2020-08-31",
                "sats":"HØY",
                "månedsberegninger": [{
                    "fraOgMed":"2020-08-01",
                    "tilOgMed":"2020-08-31",
                    "sats":"HØY",
                    "grunnbeløp":101351,
                    "beløp":19946
                }],
                "fradrag": [{
                  "type": "Arbeidsinntekt",
                  "beløp": 1000.0,
                  "utenlandskInntekt": null,
                  "periode" : {
                    "fraOgMed":"2020-08-01",
                    "tilOgMed":"2020-08-31"
                  }
                }]
            }
        """

        internal val beregning = BeregningFactory.ny(
            id = uuid,
            opprettet = LocalDateTime.of(2020, Month.AUGUST, 1, 12, 15, 15).toTidspunkt(),
            periode = Periode(1.august(2020), 31.august(2020)),
            sats = Sats.HØY,
            fradrag = listOf(
                FradragFactory.ny(
                    type = Fradragstype.Arbeidsinntekt,
                    beløp = 1000.0,
                    utenlandskInntekt = null,
                    periode = Periode(1.august(2020), 31.august(2020))
                )
            )
        )
    }

    @Test
    fun json() {
        JSONAssert.assertEquals(expectedBeregningJson.trimIndent(), serialize(beregning.toJson()), true)
    }
}
