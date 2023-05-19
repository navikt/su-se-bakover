package no.nav.su.se.bakover.web.eksterne

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.revurdering.formue.leggTilFormue
import no.nav.su.se.bakover.web.revurdering.opprettIverksattRevurdering
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson.hentSakId
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class FrikortIT {
    @Test
    fun frikort() {
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb(
            clock = TikkendeKlokke(),
        ) { appComponents ->
            val fnrA = "00000000001"
            val fnrB = "00000000002"
            val fnrC = "00000000003"
            val sakIdA = opprettInnvilgetSøknadsbehandling(
                fnr = fnrA,
                fraOgMed = januar(2021).fraOgMed.toString(),
                tilOgMed = januar(2021).tilOgMed.toString(),
                client = this.client,
                appComponents = appComponents,
            ).let { hentSakId(it) }
            opprettInnvilgetSøknadsbehandling(
                fnr = fnrB,
                fraOgMed = januar(2021).fraOgMed.toString(),
                tilOgMed = februar(2021).tilOgMed.toString(),
                client = this.client,
                appComponents = appComponents,
            )
            opprettInnvilgetSøknadsbehandling(
                fnr = fnrC,
                fraOgMed = februar(2021).fraOgMed.toString(),
                tilOgMed = februar(2021).tilOgMed.toString(),
                client = this.client,
                appComponents = appComponents,
            )
            opprettIverksattRevurdering(
                sakid = sakIdA,
                fraogmed = januar(2021).fraOgMed.toString(),
                tilogmed = januar(2021).tilOgMed.toString(),
                leggTilFormue = { sakId, behandlingId, fraOgMed, tilOgMed ->
                    leggTilFormue(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        client = this.client,
                        søkersFormue = """
                          {
                            "verdiIkkePrimærbolig": 0,
                            "verdiEiendommer": 0,
                            "verdiKjøretøy": 0,
                            "innskudd": 0,
                            "verdipapir": 0,
                            "pengerSkyldt": 0,
                            "kontanter": 200000,
                            "depositumskonto": 0
                          }
                        """.trimIndent(),
                    )
                },
                client = this.client,
                appComponents = appComponents,
            )
            // language=JSON
            JSONAssert.assertEquals(
                """
                {
                  "dato": "2021-01",
                  "fnr": ["$fnrB"]
                }
                """.trimIndent(),
                hentAktiveFnr(client = this.client),
                true,
            )
        }
    }
}

private fun hentAktiveFnr(
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    client: HttpClient,
): String {
    return runBlocking {
        SharedRegressionTestData.defaultRequest(
            HttpMethod.Get,
            "/frikort/",
            listOf(brukerrolle),
            client = client,
        ).apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
                contentType() shouldBe ContentType.parse("application/json; charset=UTF-8")
            }
        }.bodyAsText()
    }
}
