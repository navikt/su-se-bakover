package no.nav.su.se.bakover.database.revurdering

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.dokument.infrastructure.database.BrevvalgDbJson
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.saksbehandler
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
            "tidspunktAvsluttet": "2021-01-01T01:02:03.456789Z",
            "avsluttetAv": "saksbehandler"
          }
        """.trimIndent()

        JSONAssert.assertEquals(
            avsluttetJson,
            serialize(
                AvsluttetRevurderingDatabaseJson(
                    begrunnelse = "en begrunnelse",
                    brevvalg = BrevvalgDbJson("en fri tekst", null, BrevvalgDbJson.Type.SAKSBEHANDLER_VALG_SKAL_SENDE_INFORMASJONSBREV_MED_FRITEKST),
                    tidspunktAvsluttet = fixedTidspunkt,
                    avsluttetAv = saksbehandler.navIdent,
                ),
            ),
            true,
        )
    }
}
