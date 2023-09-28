package no.nav.su.se.bakover.web.revurdering

import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.fixedClock
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.revurdering.formue.leggTilFormue
import no.nav.su.se.bakover.web.revurdering.utenlandsopphold.leggTilUtenlandsoppholdRevurdering
import no.nav.su.se.bakover.web.sak.assertSakJson
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.SKIP_STEP
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.avslåttFlyktningVilkårJson
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.leggTilFlyktningVilkår
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.json.JSONObject
import org.junit.jupiter.api.Test

/**
 * I en overgangsfase støtter vi at man kan opt-out av tilbakekreving i revurderingssteget.
 * Man huker av for dette når man beregner og simulerer.
 */
internal class KanUtsetteTilbakekrevingIT {
    @Test
    fun `revurdering av eksisterende søknadsbehandling`() {
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb(
            // Skrur tiden 1 måned frem, slik at vi får en feilutbetaling
            clock = TikkendeKlokke(1.februar(2021).fixedClock()),
        ) { appComponents ->
            val stønadStart = 1.januar(2021)
            val stønadSlutt = 31.januar(2021)
            val fnr = Fnr.generer().toString()

            opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = stønadStart.toString(),
                tilOgMed = stønadSlutt.toString(),
                client = this.client,
                appComponents = appComponents,
            ).let { søknadsbehandlingJson ->

                val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)

                opprettIverksattRevurdering(
                    sakid = sakId,
                    fraogmed = stønadStart.toString(),
                    tilogmed = stønadSlutt.toString(),
                    client = this.client,
                    appComponents = appComponents,
                    leggTilUføregrunnlag = { _, _, _, _, _, _, _ -> SKIP_STEP },
                    leggTilBosituasjon = { _, _, _, _ -> SKIP_STEP },
                    leggTilFormue = { _, _, _, _ -> SKIP_STEP },
                    informasjonSomRevurderes = "[\"Flyktning\"]",
                    leggTilUtenlandsoppholdRevurdering = { _, _, _, _, _ -> SKIP_STEP },
                    leggTilFlyktningVilkår = { _, behandlingId, fraOgMed, tilOgMed, _, url ->
                        leggTilFlyktningVilkår(
                            sakId = sakId,
                            behandlingId = behandlingId,
                            fraOgMed = fraOgMed,
                            tilOgMed = tilOgMed,
                            client = client,
                            body = { avslåttFlyktningVilkårJson(fraOgMed, tilOgMed) },
                            url = url,
                        )
                    },
                    leggTilFradrag = { _, _, _, _ -> SKIP_STEP },
                    beregnOgSimuler = { _, behandlingId ->
                        beregnOgSimuler(
                            sakId = sakId,
                            behandlingId = behandlingId,
                            client = client,
                            skalUtsetteTilbakekreving = true,
                        )
                    },
                ).let { revurderingJson ->
                    hentSak(sakId, client = this.client).also {
                        assertSakJson(
                            actualSakJson = it,
                            expectedFnr = fnr,
                            expectedId = sakId,
                            expectedUtbetalingerKanStansesEllerGjenopptas = "INGEN",
                            expectedBehandlinger = "[$søknadsbehandlingJson]",
                            expectedUtbetalinger = """
                                [
                                    {
                                        "beløp":0,
                                        "fraOgMed": "2021-01-01",
                                        "tilOgMed": "2021-01-31",
                                        "type": "OPPHØR"
                                    }
                                ]
                            """.trimIndent(),
                            expectedSøknader = """
                                [
                                    ${JSONObject(søknadsbehandlingJson).getJSONObject("søknad")}
                                ]
                            """.trimIndent(),
                            expectedRevurderinger = "[$revurderingJson]",
                            expectedVedtak = """
                                [
                                    {
                                        "id":"ignore-me",
                                        "opprettet":"ignore-me",
                                        "beregning":${JSONObject(søknadsbehandlingJson).getJSONObject("beregning")},
                                        "simulering":${JSONObject(søknadsbehandlingJson).getJSONObject("simulering")},
                                        "attestant":"automatiskAttesteringAvSøknadsbehandling",
                                        "saksbehandler":"Z990Lokal",
                                        "utbetalingId":"ignore-me",
                                        "behandlingId":"ignore-me",
                                        "sakId":"ignore-me",
                                        "saksnummer":"2021",
                                        "fnr":"$fnr",
                                        "periode":{
                                          "fraOgMed":"2021-01-01",
                                          "tilOgMed":"2021-01-31"
                                        },
                                        "type":"SØKNAD",
                                        "dokumenttilstand": "GENERERT"
                                    },
                                    {
                                        "id":"ignore-me",
                                        "opprettet":"ignore-me",
                                        "beregning":${JSONObject(revurderingJson).getJSONObject("beregning")},
                                        "simulering":${JSONObject(revurderingJson).getJSONObject("simulering")},
                                        "attestant":"automatiskAttesteringAvSøknadsbehandling",
                                        "saksbehandler":"Z990Lokal",
                                        "utbetalingId":"ignore-me",
                                        "behandlingId":"ignore-me",
                                        "sakId":"ignore-me",
                                        "saksnummer":"2021",
                                        "fnr":"$fnr",
                                        "periode":{
                                          "fraOgMed":"2021-01-01",
                                          "tilOgMed":"2021-01-31"
                                        },
                                        "type":"OPPHØR",
                                        "dokumenttilstand": "GENERERT"
                                    },
                            ]
                            """.trimIndent(),
                            //language=JSON
                            expectedVedtakPåTidslinje = """
                                [
                                  {
                                       "periode":{
                                          "fraOgMed":"2021-01-01",
                                          "tilOgMed":"2021-01-31"
                                        },
                                        "vedtakType":"OPPHØR",
                                        "vedtakId": "ignore-me"
                                  }
                                ]
                            """.trimIndent(),
                        )
                    }
                }
            }
        }
    }
}
