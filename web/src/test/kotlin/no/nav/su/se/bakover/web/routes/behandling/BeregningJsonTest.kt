package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.web.routes.behandling.MånedsberegningJsonTest.Companion.expectedMånedsberegningJson
import no.nav.su.se.bakover.web.routes.behandling.MånedsberegningJsonTest.Companion.månedsberegning
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
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
                "fom":"2020-08-01",
                "tom":"2020-08-31",
                "sats":"HØY",
                "månedsberegninger": [$expectedMånedsberegningJson],
                "fradrag": [],
                "forventetInntekt": 0
            }
        """

        internal val beregning = Beregning(
            id = uuid,
            opprettet = LocalDateTime.of(2020, Month.AUGUST, 1, 12, 15, 15).toTidspunkt(),
            fom = LocalDate.of(2020, Month.AUGUST, 1),
            tom = LocalDate.of(2020, Month.AUGUST, 31),
            sats = Sats.HØY,
            månedsberegninger = mutableListOf(månedsberegning),
            fradrag = emptyList(),
            forventetInntekt = 0
        )
    }

    @Test
    fun json() {
        JSONAssert.assertEquals(expectedBeregningJson.trimIndent(), serialize(beregning.toJson()), true)
    }
}
