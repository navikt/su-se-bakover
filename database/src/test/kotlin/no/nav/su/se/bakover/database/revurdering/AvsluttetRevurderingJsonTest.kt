package no.nav.su.se.bakover.database.revurdering

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class AvsluttetRevurderingJsonTest {
    @Test
    fun `avsluttet json matcher AvsluttetRevurderingInfo`() {
        //language=JSON
        val avsluttetJson = """
          {
            "fritekst": "en fri tekst", 
            "begrunnelse": "en begrunnelse", 
            "tidspunktAvsluttet": "2021-01-01T01:02:03.456789Z"
          }
        """.trimIndent()

        JSONAssert.assertEquals(
            avsluttetJson,
            serialize(
                AvsluttetRevurderingInfo(
                    begrunnelse = "en begrunnelse",
                    fritekst = "en fri tekst",
                    tidspunktAvsluttet = fixedTidspunkt,
                ),
            ),
            true,
        )
    }
}
