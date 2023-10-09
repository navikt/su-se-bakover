package no.nav.su.se.bakover.web.tilbakekreving

import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknadOgVerifiser
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class TIlbakekrevingsbehandlingIT {

    @Test
    @Disabled // TODO jah: Vi har ikke mulighet for å skille revurdering og tilbakekreving foreløpig. Vent til vi får en egen kravgrunnlag-hendelse.
    fun `kan opprette tilbakekrevingsbehandling`() {
        val fnr = SharedRegressionTestData.fnr

        SharedRegressionTestData.withTestApplicationAndEmbeddedDb {
            val nySøknadResponseJson = nyDigitalSøknadOgVerifiser(
                fnr = fnr,
                // Første saksnummer er alltid 2021 i en ny-migrert database.
                expectedSaksnummerInResponse = 2021,
            )
            val sakId = NySøknadJson.Response.hentSakId(nySøknadResponseJson)

            opprett(sakId, client = this.client)
        }
    }

    private fun opprett(
        sakId: String,
        versjon: Long = 1,
        @Suppress("UNUSED_PARAMETER") nesteVersjon: Long = versjon + 1,
        client: HttpClient,
        expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    ) {
        JSONAssert.assertEquals(
            """{}""",
            opprettTilbakekrevingsbehandling(
                sakId = sakId,
                saksversjon = versjon,
                expectedHttpStatusCode = expectedHttpStatusCode,
                client = client,
            ),
            true,
        )
//        hentSak(sakId, client = client).also { sakJson ->
//            JSONAssert.assertEquals(
//                expectedSakResponse,
//                JSONObject(sakJson).getJSONObject("utenlandsopphold"),
//                true,
//            )
//            JSONObject(sakJson).getLong("versjon") shouldBe nesteVersjon
//        }
    }
}
