package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.Attestering
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class AttesteringTest {
    private val attestant = NavIdentBruker.Attestant("I1337")

    @Test
    fun `should serialize iverksatt`() {
        //language=json
        val expected = """
           {
           "type": "Iverksatt",
           "attestant": "I1337",
           "opprettet": "$fixedTidspunkt"
           }
        """.trimIndent()
        val actual = serialize(Attestering.Iverksatt(attestant, fixedTidspunkt))

        JSONAssert.assertEquals(expected, actual, true)
    }

    @Test
    fun `should deserialize iverksatt`() {
        //language=json
        val json = """
           {
           "type": "Iverksatt",
           "attestant": "I1337",
           "opprettet": "$fixedTidspunkt"
           }
        """.trimIndent()
        val deserialized: Attestering = deserialize(json)
        val expected = Attestering.Iverksatt(NavIdentBruker.Attestant("I1337"), fixedTidspunkt)

        deserialized shouldBe expected
    }

    @Test
    fun `should serialize underkjent`() {
        //language=json
        val expected = """
           {
           "type": "Underkjent",
           "attestant": "I1337",
           "grunn": "BEREGNINGEN_ER_FEIL",
           "kommentar": "Kan ikke dele på 0",
           "opprettet": "$fixedTidspunkt"
           }
        """.trimIndent()
        val actual = serialize(
            Attestering.Underkjent(
                attestant = NavIdentBruker.Attestant("I1337"),
                grunn = Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL,
                kommentar = "Kan ikke dele på 0",
                opprettet = fixedTidspunkt,
            ),
        )

        JSONAssert.assertEquals(expected, actual, true)
    }

    @Test
    fun `should deserialize underkjent`() {
        //language=json
        val json = """
           {
           "type": "Underkjent",
           "attestant": "I1337",
           "grunn": "BEREGNINGEN_ER_FEIL",
           "kommentar": "Kan ikke dele på 0",
           "opprettet": "$fixedTidspunkt"
             }

        """.trimIndent()
        val actual: Attestering = deserialize(json)
        val expected = Attestering.Underkjent(
            attestant = NavIdentBruker.Attestant("I1337"),
            grunn = Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL,
            kommentar = "Kan ikke dele på 0",
            opprettet = fixedTidspunkt,
        )

        actual shouldBe expected
    }
}
