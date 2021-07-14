package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.NavIdentBruker
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.Clock
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

internal class AttesteringshistorikkTest {
    private val fixedClock = Clock.fixed(1.januar(2021).startOfDay(ZoneOffset.UTC).instant, ZoneOffset.UTC)

    @Test
    fun `attesteringer er sortert etter tidspunkt`() {
        val attestering1 = Attestering.Iverksatt(
            NavIdentBruker.Attestant("Attestant1"),
            Tidspunkt.now(fixedClock).plus(1, ChronoUnit.DAYS)
        )
        val attestering2 = Attestering.Iverksatt(
            NavIdentBruker.Attestant("Attestant2"),
            Tidspunkt.now(fixedClock).plus(2, ChronoUnit.DAYS)
        )

        Attesteringshistorikk(mutableListOf(attestering2, attestering1)).hentAttesteringer() shouldBe listOf(attestering1, attestering2)
    }

    @Test
    fun `serializerer riktig`() {
        val opprettet = Tidspunkt.now(fixedClock)
        val attestering1 = Attestering.Iverksatt(NavIdentBruker.Attestant("Attestant1"), opprettet)
        val attestering2 = Attestering.Underkjent(
            NavIdentBruker.Attestant("Attestant2"),
            opprettet.plus(1, ChronoUnit.DAYS), Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL, "kommentar"
        )

        val actual = Attesteringshistorikk(mutableListOf(attestering1, attestering2)).hentAttesteringer().serialize()
        val expected = """
                [
                  {
                    "type" : "Iverksatt",
                    "attestant": "Attestant1",
                    "opprettet": "$opprettet"
                  },
                  {
                    "type" : "Underkjent",
                    "attestant": "Attestant2",
                    "opprettet": "${ opprettet.plus(1, ChronoUnit.DAYS) }",
                    "grunn" : "BEREGNINGEN_ER_FEIL",
                    "kommentar": "kommentar"
                  }
                ]
        """.trimIndent()

        JSONAssert.assertEquals(expected, actual, true)
    }

    @Test
    fun `serializerer tom liste riktig`() {
        val actual = Attesteringshistorikk(listOf()).hentAttesteringer().serialize()
        val expected = """
                []
        """.trimIndent()

        JSONAssert.assertEquals(expected, actual, true)
    }

    @Test
    fun `deserializerer riktig`() {
        val opprettet = Tidspunkt.now(fixedClock)
        val attestering1 = Attestering.Iverksatt(NavIdentBruker.Attestant("Attestant1"), opprettet)
        val attestering2 = Attestering.Underkjent(
            NavIdentBruker.Attestant("Attestant2"),
            opprettet.plus(1, ChronoUnit.DAYS), Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL, "kommentar"
        )

        val json = """
                [
                  {
                    "type" : "Iverksatt",
                    "attestant": "Attestant1",
                    "opprettet": "$opprettet"
                  },
                  {
                    "type" : "Underkjent",
                    "attestant": "Attestant2",
                    "opprettet": "${ opprettet.plus(1, ChronoUnit.DAYS) }",
                    "grunn" : "BEREGNINGEN_ER_FEIL",
                    "kommentar": "kommentar"
                  }
                ]
        """.trimIndent()

        val deserialized: List<Attestering> = json.deserializeList()
        val expected = Attesteringshistorikk(mutableListOf(attestering1, attestering2))

        Attesteringshistorikk(deserialized) shouldBe expected
    }

    @Test
    fun `legger till attestering i sluttet av listen`() {
        val opprettet = Tidspunkt.now(fixedClock)
        val attestering1 = Attestering.Underkjent(
            NavIdentBruker.Attestant("Attestant2"),
            opprettet.plus(1, ChronoUnit.DAYS), Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL, "kommentar"
        )
        val attestering2 = Attestering.Underkjent(
            NavIdentBruker.Attestant("Attestant2"),
            opprettet.plus(2, ChronoUnit.DAYS), Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL, "kommentar"
        )
        val attestering3 = Attestering.Iverksatt(NavIdentBruker.Attestant("Attestant1"), opprettet.plus(3, ChronoUnit.DAYS))

        val actual = Attesteringshistorikk.empty()
            .leggTilNyAttestering(attestering1)
            .leggTilNyAttestering(attestering2)
            .leggTilNyAttestering(attestering3)

        actual.hentAttesteringer() shouldBe listOf(attestering1, attestering2, attestering3)
    }
}
