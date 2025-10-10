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
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.juni
import no.nav.su.se.bakover.domain.vedtak.SakerMedVedtakForFrikort
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

    @Test
    fun `frikort henter alle innvilgelser og opphør`() {
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb(
            clock = TikkendeKlokke(),
        ) { appComponents ->
            val fnrA = "00000000001"
            val sakIdA = opprettInnvilgetSøknadsbehandling(
                fnr = fnrA,
                fraOgMed = januar(2021).fraOgMed.toString(),
                tilOgMed = januar(2021).tilOgMed.toString(),
                client = this.client,
                appComponents = appComponents,
            ).let { hentSakId(it) }
            opprettIverksattRevurdering(
                sakid = sakIdA,
                fraogmed = januar(2021).fraOgMed.toString(),
                tilogmed = januar(2021).tilOgMed.toString(),
                client = this.client,
                appComponents = appComponents,
            )

            val fnrB = "00000000002"
            opprettInnvilgetSøknadsbehandling(
                fnr = fnrB,
                fraOgMed = januar(2021).fraOgMed.toString(),
                tilOgMed = februar(2021).tilOgMed.toString(),
                client = this.client,
                appComponents = appComponents,
            )
            opprettInnvilgetSøknadsbehandling(
                fnr = fnrB,
                fraOgMed = april(2021).fraOgMed.toString(),
                tilOgMed = juni(2021).tilOgMed.toString(),
                client = this.client,
                appComponents = appComponents,
            )

            val fnrC = "00000000003"
            val sakidC = opprettInnvilgetSøknadsbehandling(
                fnr = fnrC,
                fraOgMed = februar(2021).fraOgMed.toString(),
                tilOgMed = februar(2021).tilOgMed.toString(),
                client = this.client,
                appComponents = appComponents,
            ).let { hentSakId(it) }
            opprettIverksattRevurdering(
                sakid = sakidC,
                fraogmed = februar(2021).fraOgMed.toString(),
                tilogmed = februar(2021).tilOgMed.toString(),
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
            opprettIverksattRevurdering(
                sakid = sakidC,
                fraogmed = februar(2021).fraOgMed.toString(),
                tilogmed = februar(2021).tilOgMed.toString(),
                client = this.client,
                appComponents = appComponents,
            )

            val result = deserialize<SakerMedVedtakForFrikort>(hentAlle(client = this.client))
            with(result) {
                // Bygging av testdata setter alltid 1. januar til opprettet
                val opprettet = januar(2021).fraOgMed

                saker.size shouldBe 3

                with(saker[0]) {
                    fnr shouldBe fnrA
                    vedtak.size shouldBe 2

                    vedtak[0].fraOgMed shouldBe januar(2021).fraOgMed
                    vedtak[0].tilOgMed shouldBe januar(2021).tilOgMed
                    vedtak[0].type shouldBe "SØKNADSBEHANDLING_INNVILGELSE"
                    vedtak[0].opprettet.toLocalDate() shouldBe opprettet

                    vedtak[1].fraOgMed shouldBe januar(2021).fraOgMed
                    vedtak[1].tilOgMed shouldBe januar(2021).tilOgMed
                    vedtak[1].type shouldBe "REVURDERING_INNVILGELSE"
                    vedtak[1].opprettet.toLocalDate() shouldBe opprettet
                }

                with(saker[1]) {
                    fnr shouldBe fnrB
                    vedtak.size shouldBe 2

                    vedtak[0].fraOgMed shouldBe januar(2021).fraOgMed
                    vedtak[0].tilOgMed shouldBe februar(2021).tilOgMed
                    vedtak[0].type shouldBe "SØKNADSBEHANDLING_INNVILGELSE"
                    vedtak[0].opprettet.toLocalDate() shouldBe opprettet

                    vedtak[1].fraOgMed shouldBe april(2021).fraOgMed
                    vedtak[1].tilOgMed shouldBe juni(2021).tilOgMed
                    vedtak[1].type shouldBe "SØKNADSBEHANDLING_INNVILGELSE"
                    vedtak[1].opprettet.toLocalDate() shouldBe opprettet
                }

                with(saker[2]) {
                    fnr shouldBe fnrC
                    vedtak.size shouldBe 3

                    vedtak[0].fraOgMed shouldBe februar(2021).fraOgMed
                    vedtak[0].tilOgMed shouldBe februar(2021).tilOgMed
                    vedtak[0].type shouldBe "SØKNADSBEHANDLING_INNVILGELSE"
                    vedtak[0].opprettet.toLocalDate() shouldBe opprettet

                    vedtak[1].fraOgMed shouldBe februar(2021).fraOgMed
                    vedtak[1].tilOgMed shouldBe februar(2021).tilOgMed
                    vedtak[1].type shouldBe "REVURDERING_OPPHØR"
                    vedtak[1].opprettet.toLocalDate() shouldBe opprettet

                    vedtak[2].fraOgMed shouldBe februar(2021).fraOgMed
                    vedtak[2].tilOgMed shouldBe februar(2021).tilOgMed
                    vedtak[2].type shouldBe "REVURDERING_INNVILGELSE"
                    vedtak[2].opprettet.toLocalDate() shouldBe opprettet
                }
            }
        }
    }
}

private fun hentAktiveFnr(
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    client: HttpClient,
): String {
    return runBlocking {
        no.nav.su.se.bakover.test.application.defaultRequest(
            HttpMethod.Get,
            "/frikort/",
            listOf(brukerrolle),
            client = client,
        ).apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
                contentType() shouldBe ContentType.parse("application/json")
            }
        }.bodyAsText()
    }
}

private fun hentAlle(
    brukerrolle: Brukerrolle = Brukerrolle.Saksbehandler,
    client: HttpClient,
): String {
    return runBlocking {
        no.nav.su.se.bakover.test.application.defaultRequest(
            HttpMethod.Get,
            "/frikort/alle",
            listOf(brukerrolle),
            client = client,
        ).apply {
            withClue("body=${this.bodyAsText()}") {
                status shouldBe HttpStatusCode.OK
                contentType() shouldBe ContentType.parse("application/json")
            }
        }.bodyAsText()
    }
}
