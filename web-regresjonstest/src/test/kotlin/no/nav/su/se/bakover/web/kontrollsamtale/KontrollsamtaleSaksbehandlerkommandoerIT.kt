package no.nav.su.se.bakover.web.kontrollsamtale

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.domain.tid.endOfMonth
import no.nav.su.se.bakover.common.domain.tid.startOfMonth
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.json.shouldBeSimilarJsonTo
import no.nav.su.se.bakover.test.persistence.DbExtension
import no.nav.su.se.bakover.web.SharedRegressionTestData.withTestApplicationAndEmbeddedDb
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import javax.sql.DataSource

@ExtendWith(DbExtension::class)
internal class KontrollsamtaleSaksbehandlerkommandoerIT(private val dataSource: DataSource) {
    @Test
    fun `opprett, endre og annuller kontrollsamtale`() {
        withTestApplicationAndEmbeddedDb(dataSource) { appComponents ->
            val fnr = Fnr.generer().toString()
            val opprettSøknadsbehandlingResponseJson = opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = fixedLocalDate.startOfMonth().toString(),
                tilOgMed = fixedLocalDate.plusMonths(11).endOfMonth().toString(),
                client = this.client,
                appComponents = appComponents,
            )
            val sakId = BehandlingJson.hentSakId(opprettSøknadsbehandlingResponseJson)

            fun assertKontrollsamtale(
                actual: KontrollsamtaleResponseJson,
                id: String,
                innkallingsdato: String = "2021-05-01",
                frist: String = "2021-05-31",
                status: String = "PLANLAGT_INNKALLING",
                kanOppdatereInnkallingsmåned: Boolean = true,
                lovligeStatusovergangerForSaksbehandler: List<String> = listOf("ANNULLERT"),
                hendelser: List<String> = emptyList(),
            ) {
                actual.id shouldBe id
                actual.opprettet shouldBe "2021-01-01T01:02:03.456789Z"
                actual.innkallingsdato shouldBe innkallingsdato
                actual.status shouldBe status
                actual.frist shouldBe frist
                actual.dokumentId shouldBe null
                actual.journalpostIdKontrollnotat shouldBe null
                actual.kanOppdatereInnkallingsmåned shouldBe kanOppdatereInnkallingsmåned
                actual.lovligeStatusovergangerForSaksbehandler shouldBe lovligeStatusovergangerForSaksbehandler
                actual.hendelser.map { it.handling } shouldBe hendelser
                if (hendelser.isNotEmpty()) {
                    actual.hendelser.map { it.rolle }.distinct() shouldBe listOf("SAKSBEHANDLER")
                }
            }

            hentKontrollsamtalerForSakId(sakId, client = this.client).also { actual ->
                actual.toKontrollsamtalerResponseJson().also {
                    it.size shouldBe 1
                    assertKontrollsamtale(actual = it.single(), id = it.single().id)
                }
            }
            val kontrollsamtaleId =
                hentNestePlanlagteKontrollsamtalerForSakId(sakId, client = this.client).also { actual ->
                    assertKontrollsamtale(
                        actual = actual.toKontrollsamtaleResponseJson(),
                        id = actual.toKontrollsamtaleResponseJson().id,
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
                assertKontrollsamtale(
                    actual = actual.toKontrollsamtaleResponseJson(),
                    id = kontrollsamtaleId,
                    innkallingsdato = "2021-02-01",
                    frist = "2021-02-28",
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
                assertKontrollsamtale(
                    actual = actual.toKontrollsamtaleResponseJson(),
                    id = actual.toKontrollsamtaleResponseJson().id,
                    innkallingsdato = "2021-04-01",
                    frist = "2021-04-30",
                    hendelser = listOf("PLANLAGT_INNKALLING"),
                )
            }
            annullerKontrollsamtale(
                sakId = sakId,
                kontrollsamtaleId = kontrollsamtaleId,
                client = this.client,
            ).also { actual ->
                assertKontrollsamtale(
                    actual = actual.toKontrollsamtaleResponseJson(),
                    id = kontrollsamtaleId,
                    innkallingsdato = "2021-02-01",
                    frist = "2021-02-28",
                    status = "ANNULLERT",
                    kanOppdatereInnkallingsmåned = false,
                    lovligeStatusovergangerForSaksbehandler = emptyList(),
                    hendelser = listOf("ANNULLERT"),
                )
            }
        }
    }
}
