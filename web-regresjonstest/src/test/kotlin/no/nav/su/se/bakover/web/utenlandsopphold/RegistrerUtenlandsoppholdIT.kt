package no.nav.su.se.bakover.web.utenlandsopphold

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
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
                expectedSaksnummerInResponse = 2021, // Første saksnummer er alltid 2021 i en ny-migrert database.
            )
            val sakId = NySøknadJson.Response.hentSakId(nySøknadResponseJson)

            registrer(sakId)
            korriger(sakId)
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
                        ),
                    ),
                ),
            )
            annuller(sakId)
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
                            erAnnullert = true,
                        ),
                    ),
                ),
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
                            erAnnullert = true,
                        ),
                        UtenlandsResponseJsonData(versjon = 5),
                    ),
                ),
            )
        }
    }

    private fun ApplicationTestBuilder.annuller(
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
                    erAnnullert = true,
                ),
            ),
        ),
        expectedSakResponse: String = expectedAnnullerResponse,
    ) {
        JSONAssert.assertEquals(
            expectedAnnullerResponse,
            this.annullerUtenlandsopphold(
                sakId = sakId,
                saksversjon = versjon,
                annullererVersjon = versjon,
                expectedHttpStatusCode = expectedHttpStatusCode,
            ),
            true,
        )
        hentSak(sakId).also { sakJson ->
            JSONAssert.assertEquals(
                expectedSakResponse,
                JSONObject(sakJson).getJSONObject("utenlandsopphold"),
                true,
            )
            JSONObject(sakJson).getLong("versjon") shouldBe nesteVersjon
        }
    }

    private fun ApplicationTestBuilder.korriger(sakId: String) {
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
                ),
            ),
        )
        JSONAssert.assertEquals(
            expectedKorrigerResponse,
            this.korrigerUtenlandsopphold(
                sakId = sakId,
                fraOgMed = "2021-05-04",
                tilOgMed = "2021-10-11",
                journalpostIder = "[\"12121212\"]",
                dokumentasjon = "Udokumentert",
                saksversjon = 2,
                korrigererVersjon = 2,
            ),
            true,
        )
        hentSak(sakId).also { sakJson ->
            JSONAssert.assertEquals(
                expectedKorrigerResponse,
                JSONObject(sakJson).getJSONObject("utenlandsopphold"),
                true,
            )
            JSONObject(sakJson).getLong("versjon") shouldBe 3L
        }
    }

    private fun ApplicationTestBuilder.registrer(
        sakId: String,
        versjon: Long = 1,
        nesteVersjon: Long = versjon + 1,
        expectedRegistrertResponse: String = utenlandsoppholdResponseJson(
            elements = listOf(UtenlandsResponseJsonData(versjon = nesteVersjon)),
        ),
        expectedSakResponse: String = expectedRegistrertResponse,
        expectedHttpStatusCode: HttpStatusCode = HttpStatusCode.Created,
    ) {
        JSONAssert.assertEquals(
            expectedRegistrertResponse,
            this.nyttUtenlandsopphold(
                sakId = sakId,
                saksversjon = versjon,
                expectedHttpStatusCode = expectedHttpStatusCode,
            ),
            true,
        )
        hentSak(sakId).also { sakJson ->
            JSONAssert.assertEquals(
                expectedSakResponse,
                JSONObject(sakJson).getJSONObject("utenlandsopphold"),
                true,
            )
            JSONObject(sakJson).getLong("versjon") shouldBe nesteVersjon
        }
    }
}
