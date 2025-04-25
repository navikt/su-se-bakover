package no.nav.su.se.bakover.web.kontrollsamtale

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.domain.tid.endOfMonth
import no.nav.su.se.bakover.common.domain.tid.startOfMonth
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.web.SharedRegressionTestData.withTestApplicationAndEmbeddedDb
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test

internal class KontrollsamtaleSaksbehandlerkommandoerIT {
    @Test
    fun `opprett, endre og annuller kontrollsamtale`() {
        withTestApplicationAndEmbeddedDb { appComponents ->
            val fnr = Fnr.generer().toString()
            val opprettSøknadsbehandlingResponseJson = opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = fixedLocalDate.startOfMonth().toString(),
                tilOgMed = fixedLocalDate.plusMonths(11).endOfMonth().toString(),
                client = this.client,
                appComponents = appComponents,
            )
            val sakId = BehandlingJson.hentSakId(opprettSøknadsbehandlingResponseJson)

            fun expectedKontrollsamtale(
                id: String,
                innkallingsdato: String = "2021-05-01",
                frist: String = "2021-05-31",
                status: String = "PLANLAGT_INNKALLING",
                kanOppdatereInnkallingsmåned: Boolean = true,
                lovligeStatusovergangerForSaksbehandler: List<String> = listOf("ANNULLERT"),
            ) = """
                    {
                        "id":"$id",
                        "opprettet":"2021-01-01T01:02:03.456789Z",
                        "innkallingsdato":"$innkallingsdato",
                        "status":"$status",
                        "frist":"$frist",
                        "dokumentId":null,
                        "journalpostIdKontrollnotat":null,
                        "kanOppdatereInnkallingsmåned":$kanOppdatereInnkallingsmåned,
                        "lovligeStatusovergangerForSaksbehandler":[${
                lovligeStatusovergangerForSaksbehandler.joinToString(
                    separator = ",",
                ) { """"$it"""" }
            }]}
            """.trimIndent()
            hentKontrollsamtalerForSakId(sakId, client = this.client).also { actual ->
                actual.shouldBeSimilarJsonTo(
                    expectedJson = "[${expectedKontrollsamtale(id = "ignore-me")}]",
                    "[*].id",
                )
            }
            val kontrollsamtaleId =
                hentNestePlanlagteKontrollsamtalerForSakId(sakId, client = this.client).also { actual ->
                    actual.shouldBeSimilarJsonTo(
                        expectedJson = expectedKontrollsamtale(id = "ignore-me"),
                        "id",
                    )
                }.let { hentKontrollsamtaleId(it) }

            // Tester at vi ikke kan sette planlagt innkalling til inneværende måned.
            oppdaterInnkallingsmånedPåKontrollsamtale(
                sakId = sakId,
                kontrollsamtaleId = kontrollsamtaleId,
                innkallingsmåned = "2021-01",
                client = this.client,
                expectedStatus = HttpStatusCode.BadRequest,
            ).also { actual ->
                actual.shouldBeSimilarJsonTo(
                    expectedJson = """
                        {
                            "message": "Innkallingsmåned må være etter nåværende måned",
                            "code": "innkallingsmåned_må_være_etter_nåværende_måned"
                        }
                    """.trimIndent(),
                )
            }
            // Tester at vi kan oppdatere innkallingsmåned til neste måned
            oppdaterInnkallingsmånedPåKontrollsamtale(
                sakId = sakId,
                kontrollsamtaleId = kontrollsamtaleId,
                innkallingsmåned = "2021-02",
                client = this.client,
            ).also { actual ->
                actual.shouldBeSimilarJsonTo(
                    expectedJson = expectedKontrollsamtale(
                        id = kontrollsamtaleId,
                        innkallingsdato = "2021-02-01",
                        frist = "2021-02-28",
                    ),
                )
            }
            // Test at 2 kontrollsamtaler ikke kan ha samme innkallingsmåned
            opprettKontrollsamtale(
                sakId = sakId,
                innkallingsmåned = "2021-02",
                client = this.client,
                expectedStatus = HttpStatusCode.BadRequest,
            ).also { actual ->
                actual.shouldBeSimilarJsonTo(
                    expectedJson = """
                        {
                            "message": "Ugyldig innkallingsmåned",
                            "code": "ugyldig_innkallingsmåned"
                        }
                    """.trimIndent(),
                )
            }
            // Test at 2 kontrollsamtaler ikke kan være sammenhengende
            opprettKontrollsamtale(
                sakId = sakId,
                innkallingsmåned = "2021-03",
                client = this.client,
                expectedStatus = HttpStatusCode.BadRequest,
            ).also { actual ->
                actual.shouldBeSimilarJsonTo(
                    expectedJson = """
                        {
                            "message": "Ugyldig innkallingsmåned",
                            "code": "ugyldig_innkallingsmåned"
                        }
                    """.trimIndent(),
                )
            }
            // Happy case opprett
            opprettKontrollsamtale(
                sakId = sakId,
                innkallingsmåned = "2021-04",
                client = this.client,
            ).also { actual ->
                actual.shouldBeSimilarJsonTo(
                    expectedJson = expectedKontrollsamtale(
                        id = "kan-ikke-teste-id",
                        innkallingsdato = "2021-04-01",
                        frist = "2021-04-30",
                    ),
                    "id",
                )
            }
            annullerKontrollsamtale(
                sakId = sakId,
                kontrollsamtaleId = kontrollsamtaleId,
                client = this.client,
            ).also { actual ->
                actual.shouldBeSimilarJsonTo(
                    expectedJson = expectedKontrollsamtale(
                        id = kontrollsamtaleId,
                        innkallingsdato = "2021-02-01",
                        frist = "2021-02-28",
                        status = "ANNULLERT",
                        kanOppdatereInnkallingsmåned = false,
                        lovligeStatusovergangerForSaksbehandler = emptyList(),
                    ),
                )
            }
        }
    }
}
