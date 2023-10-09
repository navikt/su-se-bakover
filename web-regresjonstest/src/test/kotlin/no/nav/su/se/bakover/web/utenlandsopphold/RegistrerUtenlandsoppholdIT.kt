package no.nav.su.se.bakover.web.utenlandsopphold

import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknadOgVerifiser
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class RegistrerUtenlandsoppholdIT {

    @Test
    fun `kan registere, korrigere og anullere utenlandsopphold`() {
        val fnr = SharedRegressionTestData.fnr

        SharedRegressionTestData.withTestApplicationAndEmbeddedDb {
            val nySøknadResponseJson = nyDigitalSøknadOgVerifiser(
                fnr = fnr,
                // Første saksnummer er alltid 2021 i en ny-migrert database.
                expectedSaksnummerInResponse = 2021,
            )
            val sakId = NySøknadJson.Response.hentSakId(nySøknadResponseJson)

            registrer(sakId, client = this.client)
            korriger(sakId, client = this.client)
            registrer(
                sakId = sakId,
                versjon = 3,
                nesteVersjon = 3,
                expectedHttpStatusCode = HttpStatusCode.BadRequest,
                expectedRegistrertResponse = """
                {
                "code":"overlappende_perioder",
                "message":"Ønsket periode overlapper med tidligere perioder"
                }
                """.trimIndent(),
                expectedSakResponse = utenlandsoppholdResponseJson(
                    antallDagerTotal = 159,
                    elements = listOf(
                        UtenlandsResponseJsonData(
                            versjon = 3,
                            antallDagerForPeriode = 159,
                            dokumentasjon = "Udokumentert",
                            fraOgMed = "2021-05-04",
                            tilOgMed = "2021-10-11",
                            journalpostIder = "[\"12121212\"]",
                            begrunnelse = "Linket til feil journalpost. Utenlandsoppholdet er udokumentert",
                        ),
                    ),
                ),
                client = this.client,
            )
            annuller(sakId, client = this.client)
            annuller(
                versjon = 4,
                nesteVersjon = 4,
                sakId = sakId,
                expectedHttpStatusCode = HttpStatusCode.InternalServerError,
                expectedAnnullerResponse = """
                    {
                        "code":"ukjent_feil",
                        "message":"Ukjent feil"
                    }
                """.trimIndent(),
                expectedSakResponse = utenlandsoppholdResponseJson(
                    antallDagerTotal = 0,
                    elements = listOf(
                        UtenlandsResponseJsonData(
                            versjon = 4,
                            antallDagerForPeriode = 159,
                            dokumentasjon = "Udokumentert",
                            fraOgMed = "2021-05-04",
                            tilOgMed = "2021-10-11",
                            journalpostIder = "[\"12121212\"]",
                            begrunnelse = "Linket til feil journalpost. Utenlandsoppholdet er udokumentert",
                            erAnnullert = true,
                        ),
                    ),
                ),
                client = this.client,
            )
            registrer(
                sakId = sakId,
                versjon = 4,
                expectedRegistrertResponse = utenlandsoppholdResponseJson(
                    elements = listOf(
                        UtenlandsResponseJsonData(
                            versjon = 4,
                            antallDagerForPeriode = 159,
                            dokumentasjon = "Udokumentert",
                            fraOgMed = "2021-05-04",
                            tilOgMed = "2021-10-11",
                            journalpostIder = "[\"12121212\"]",
                            begrunnelse = "Linket til feil journalpost. Utenlandsoppholdet er udokumentert",
                            erAnnullert = true,
                        ),
                        UtenlandsResponseJsonData(versjon = 5),
                    ),
                ),
                client = this.client,
            )
        }
    }

    private fun annuller(
        sakId: String,
        versjon: Long = 3,
        nesteVersjon: Long = versjon + 1,
        expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.OK,
        expectedAnnullerResponse: String = utenlandsoppholdResponseJson(
            antallDagerTotal = 0,
            elements = listOf(
                UtenlandsResponseJsonData(
                    versjon = nesteVersjon,
                    antallDagerForPeriode = 159,
                    dokumentasjon = "Udokumentert",
                    fraOgMed = "2021-05-04",
                    tilOgMed = "2021-10-11",
                    journalpostIder = "[\"12121212\"]",
                    begrunnelse = "Linket til feil journalpost. Utenlandsoppholdet er udokumentert",
                    erAnnullert = true,
                ),
            ),
        ),
        expectedSakResponse: String = expectedAnnullerResponse,
        client: HttpClient,
    ) {
        JSONAssert.assertEquals(
            expectedAnnullerResponse,
            annullerUtenlandsopphold(
                sakId = sakId,
                saksversjon = versjon,
                annullererVersjon = versjon,
                expectedHttpStatusCode = expectedHttpStatusCode,
                client = client,
            ),
            true,
        )
        hentSak(sakId, client = client).also { sakJson ->
            JSONAssert.assertEquals(
                expectedSakResponse,
                JSONObject(sakJson).getJSONObject("utenlandsopphold"),
                true,
            )
            JSONObject(sakJson).getLong("versjon") shouldBe nesteVersjon
        }
    }

    private fun korriger(sakId: String, client: HttpClient) {
        val expectedKorrigerResponse = utenlandsoppholdResponseJson(
            antallDagerTotal = 159,
            elements = listOf(
                UtenlandsResponseJsonData(
                    versjon = 3,
                    antallDagerForPeriode = 159,
                    dokumentasjon = "Udokumentert",
                    fraOgMed = "2021-05-04",
                    tilOgMed = "2021-10-11",
                    journalpostIder = "[\"12121212\"]",
                    begrunnelse = "Linket til feil journalpost. Utenlandsoppholdet er udokumentert",
                ),
            ),
        )
        JSONAssert.assertEquals(
            expectedKorrigerResponse,
            korrigerUtenlandsopphold(
                sakId = sakId,
                fraOgMed = "2021-05-04",
                tilOgMed = "2021-10-11",
                journalpostIder = "[\"12121212\"]",
                dokumentasjon = "Udokumentert",
                begrunnelse = "Linket til feil journalpost. Utenlandsoppholdet er udokumentert",
                saksversjon = 2,
                korrigererVersjon = 2,
                client = client,
            ),
            true,
        )
        hentSak(sakId, client).also { sakJson ->
            JSONAssert.assertEquals(
                expectedKorrigerResponse,
                JSONObject(sakJson).getJSONObject("utenlandsopphold"),
                true,
            )
            JSONObject(sakJson).getLong("versjon") shouldBe 3L
        }
    }

    private fun registrer(
        sakId: String,
        versjon: Long = 1,
        nesteVersjon: Long = versjon + 1,
        client: HttpClient,
        expectedRegistrertResponse: String = utenlandsoppholdResponseJson(
            elements = listOf(UtenlandsResponseJsonData(versjon = nesteVersjon)),
        ),
        expectedSakResponse: String = expectedRegistrertResponse,
        expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    ) {
        JSONAssert.assertEquals(
            expectedRegistrertResponse,
            nyttUtenlandsopphold(
                sakId = sakId,
                saksversjon = versjon,
                expectedHttpStatusCode = expectedHttpStatusCode,
                client = client,
            ),
            true,
        )
        hentSak(sakId, client = client).also { sakJson ->
            JSONAssert.assertEquals(
                expectedSakResponse,
                JSONObject(sakJson).getJSONObject("utenlandsopphold"),
                true,
            )
            JSONObject(sakJson).getLong("versjon") shouldBe nesteVersjon
        }
    }
}
