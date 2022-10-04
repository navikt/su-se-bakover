package no.nav.su.se.bakover.database.attestering

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import java.time.temporal.ChronoUnit

internal class AttesteringerJsonTest {

    @Test
    fun `serialiserer riktig`() {
        val opprettet = fixedTidspunkt
        val attestering1 = Attestering.Iverksatt(NavIdentBruker.Attestant("Attestant1"), opprettet)
        val attestering2 = Attestering.Underkjent(
            NavIdentBruker.Attestant("Attestant2"),
            opprettet.plus(1, ChronoUnit.DAYS),
            Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL,
            "kommentar",
        )
        val actual = Attesteringshistorikk.create(mutableListOf(attestering1, attestering2)).toDatabaseJson()
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
                    "opprettet": "${opprettet.plus(1, ChronoUnit.DAYS)}",
                    "grunn" : "BEREGNINGEN_ER_FEIL",
                    "kommentar": "kommentar"
                  }
                ]
        """.trimIndent()

        JSONAssert.assertEquals(expected, actual, true)
    }

    @Test
    fun `serializerer tom liste riktig`() {
        JSONAssert.assertEquals("[]", Attesteringshistorikk.create(listOf()).toDatabaseJson(), true)
    }

    @Test
    fun `deserializerer riktig`() {
        val opprettet = fixedTidspunkt
        val attestering1 = Attestering.Iverksatt(NavIdentBruker.Attestant("Attestant1"), opprettet)
        val attestering2 = Attestering.Underkjent(
            NavIdentBruker.Attestant("Attestant2"),
            opprettet.plus(1, ChronoUnit.DAYS),
            Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL,
            "kommentar",
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
                    "opprettet": "${opprettet.plus(1, ChronoUnit.DAYS)}",
                    "grunn" : "BEREGNINGEN_ER_FEIL",
                    "kommentar": "kommentar"
                  }
                ]
        """.trimIndent()

        val deserialized = json.toAttesteringshistorikk()
        val expected = Attesteringshistorikk.create(mutableListOf(attestering1, attestering2))

        deserialized shouldBe expected
    }

    @Test
    fun `deserializerer tom liste riktig`() {
        "[]".toAttesteringshistorikk() shouldBe Attesteringshistorikk.empty()
    }
}
