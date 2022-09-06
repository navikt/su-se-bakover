package no.nav.su.se.bakover.database.revurdering

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.brev.BrevvalgDatabaseJson
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class AvsluttetRevurderingJsonTest {
    @Test
    fun `avsluttet json matcher AvsluttetRevurderingInfo`() {
        //language=JSON
        val avsluttetJson = """
          {
            "brevvalg": {
              "fritekst": "en fri tekst", 
              "begrunnelse": null,
              "type": "SAKSBEHANDLER_VALG_SKAL_SENDE_INFORMASJONSBREV_MED_FRITEKST"
            },
            "begrunnelse": "en begrunnelse", 
            "tidspunktAvsluttet": "2021-01-01T01:02:03.456789Z"
          }
        """.trimIndent()

        JSONAssert.assertEquals(
            avsluttetJson,
            serialize(
                AvsluttetRevurderingDatabaseJson(
                    begrunnelse = "en begrunnelse",
                    brevvalg = BrevvalgDatabaseJson("en fri tekst", null, BrevvalgDatabaseJson.Type.SAKSBEHANDLER_VALG_SKAL_SENDE_INFORMASJONSBREV_MED_FRITEKST),
                    tidspunktAvsluttet = fixedTidspunkt,
                ),
            ),
            true,
        )
    }
}
