package no.nav.su.se.bakover.web.dokumenter

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import no.nav.su.se.bakover.test.jsonAssertEquals
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknadOgVerifiser
import org.json.JSONObject
import org.junit.jupiter.api.Test

internal class LeggTilDokumentPåSakIT {

    @Test
    fun `oppretter, lagrer & sender fritekst-dokument for sak`() {
        val fnr = SharedRegressionTestData.fnr

        SharedRegressionTestData.withTestApplicationAndEmbeddedDb {
            val nySøknadResponseJson = nyDigitalSøknadOgVerifiser(
                fnr = fnr,
                expectedSaksnummerInResponse = 2021, // Første saksnummer er alltid 2021 i en ny-migrert database.
            )
            val sakId = NySøknadJson.Response.hentSakId(nySøknadResponseJson)

            opprettFritekstDokument(
                sakId = sakId,
                expectedOpprettResponse = SharedRegressionTestData.pdf,
                this.client,
            )

            lagreOgSendFritekstDokument(
                sakId = sakId,
                //language=JSON
                expectedLagreOgSendResponse = """
                            {
                              "id": "IGNORERT",
                              "tittel":"Fritekst-brevets tittel",
                              "opprettet": "IGNORERT",
                              "dokument": "${SharedRegressionTestData.dokumentData}",
                              "journalført":false,
                              "brevErBestilt":false
                            }
                """.trimIndent(),
                this.client,
            ).let { JSONObject(it).get("id").toString() }
        }
    }

    private fun opprettFritekstDokument(
        sakId: String,
        expectedOpprettResponse: String,
        client: HttpClient,
    ) = no.nav.su.se.bakover.web.dokumenter.opprettFritekstDokument(sakId = sakId, client = client).also {
        it shouldBe expectedOpprettResponse
    }

    private fun lagreOgSendFritekstDokument(
        sakId: String,
        expectedLagreOgSendResponse: String,
        client: HttpClient,
    ) = no.nav.su.se.bakover.web.dokumenter.lagreOgSendFritekstDokument(sakId = sakId, client = client).also {
        jsonAssertEquals(
            expectedLagreOgSendResponse,
            it,
            "id",
            "opprettet",
        )
    }
}
