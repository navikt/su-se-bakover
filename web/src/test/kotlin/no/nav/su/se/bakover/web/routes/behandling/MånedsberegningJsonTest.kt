package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.Sats
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneOffset
import java.util.UUID

internal class MånedsberegningJsonTest {
    companion object {
        private val uuid = UUID.randomUUID()

        //language=JSON
        internal val expectedMånedsberegningJson =
            """
            {
                "id":"$uuid",
                "fom":"2020-01-01",
                "tom":"2020-12-31",
                "sats":"HØY",
                "grunnbeløp":15000,
                "beløp":25000
            }
        """

        internal val månedsberegning = Månedsberegning(
            id = uuid,
            opprettet = LocalDateTime.of(2020, Month.JANUARY, 1, 12, 0, 0).toInstant(ZoneOffset.UTC),
            fom = LocalDate.of(2020, Month.JANUARY, 1),
            tom = LocalDate.of(2020, Month.DECEMBER, 31),
            sats = Sats.HØY,
            grunnbeløp = 15000,
            beløp = 25000,
            fradrag = 0
        )
    }

    @Test
    fun json() {
        JSONAssert.assertEquals(
            expectedMånedsberegningJson.trimIndent(),
            serialize(månedsberegning.toJson()),
            true
        )
    }
}
