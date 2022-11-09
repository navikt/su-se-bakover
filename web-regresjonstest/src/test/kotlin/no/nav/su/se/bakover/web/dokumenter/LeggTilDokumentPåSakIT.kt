package no.nav.su.se.bakover.web.dokumenter

import io.kotest.matchers.shouldBe
import io.ktor.server.testing.ApplicationTestBuilder
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
                expectedOpprettResponse =
                """%PDF-1.0
                1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj 2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj 3 0 obj<</Type/Page/MediaBox[0 0 3 3]>>endobj
                xref
                0 4
                0000000000 65535 f
                0000000010 00000 n
                0000000053 00000 n
                0000000102 00000 n
                trailer<</Size 4/Root 1 0 R>>
                startxref
                149
                %EOF
                """.trimIndent(),

            )

            lagreOgSendFritekstDokument(
                sakId = sakId,
                //language=JSON
                expectedLagreOgSendResponse = """
                            {
                              "id": "IGNORERT",
                              "tittel":"Fritekst-brevets tittel",
                              "opprettet": "IGNORERT",
                              "dokument":"JVBERi0xLjAKICAgICAgICAgICAgICAgIDEgMCBvYmo8PC9UeXBlL0NhdGFsb2cvUGFnZXMgMiAwIFI+PmVuZG9iaiAyIDAgb2JqPDwvVHlwZS9QYWdlcy9LaWRzWzMgMCBSXS9Db3VudCAxPj5lbmRvYmogMyAwIG9iajw8L1R5cGUvUGFnZS9NZWRpYUJveFswIDAgMyAzXT4+ZW5kb2JqCiAgICAgICAgICAgICAgICB4cmVmCiAgICAgICAgICAgICAgICAwIDQKICAgICAgICAgICAgICAgIDAwMDAwMDAwMDAgNjU1MzUgZgogICAgICAgICAgICAgICAgMDAwMDAwMDAxMCAwMDAwMCBuCiAgICAgICAgICAgICAgICAwMDAwMDAwMDUzIDAwMDAwIG4KICAgICAgICAgICAgICAgIDAwMDAwMDAxMDIgMDAwMDAgbgogICAgICAgICAgICAgICAgdHJhaWxlcjw8L1NpemUgNC9Sb290IDEgMCBSPj4KICAgICAgICAgICAgICAgIHN0YXJ0eHJlZgogICAgICAgICAgICAgICAgMTQ5CiAgICAgICAgICAgICAgICAlRU9G",
                              "journalført":false,
                              "brevErBestilt":false
                            }
                """.trimIndent(),
            ).let { JSONObject(it).get("id").toString() }
        }
    }

    private fun ApplicationTestBuilder.opprettFritekstDokument(
        sakId: String,
        expectedOpprettResponse: String,
    ) = this.opprettFritekstDokument(sakId = sakId).also {
        it shouldBe expectedOpprettResponse
    }

    private fun ApplicationTestBuilder.lagreOgSendFritekstDokument(
        sakId: String,
        expectedLagreOgSendResponse: String,
    ) = this.lagreOgSendFritekstDokument(sakId = sakId).also {
        jsonAssertEquals(
            expectedLagreOgSendResponse,
            it,
            "id",
            "opprettet",
        )
    }
}
