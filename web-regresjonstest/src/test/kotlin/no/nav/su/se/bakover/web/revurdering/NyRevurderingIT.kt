package no.nav.su.se.bakover.web.revurdering

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.sak.assertSakJson
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.json.JSONObject
import org.junit.jupiter.api.Test

internal class NyRevurderingIT {
    @Test
    fun `revurdering av eksisterende søknadsbehandling`() {
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb(
            clock = TikkendeKlokke(fixedClock),
        ) {
            val stønadStart = 1.januar(2021)
            val stønadSlutt = 31.desember(2021)
            val fnr = Fnr.generer().toString()

            opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = stønadStart.toString(),
                tilOgMed = stønadSlutt.toString(),
                client = this.client,
            ).let { søknadsbehandlingJson ->

                val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)

                opprettIverksattRevurdering(
                    sakid = sakId,
                    fraogmed = 1.mai(2021).toString(),
                    tilogmed = 31.juli(2021).toString(),
                    client = this.client,
                ).let { revurderingJson ->
                    hentSak(sakId, client = this.client).also {
                        assertSakJson(
                            actualSakJson = it,
                            expectedFnr = fnr,
                            expectedId = sakId,
                            expectedUtbetalingerKanStansesEllerGjenopptas = "STANS",
                            expectedBehandlinger = "[$søknadsbehandlingJson]",
                            expectedUtbetalinger = """
                                [
                                    {
                                        "beløp":20946,
                                        "fraOgMed": "2021-01-01",
                                        "tilOgMed": "2021-04-30",
                                        "type": "NY"
                                    },
                                    {
                                        "beløp":8563,
                                        "fraOgMed": "2021-05-01",
                                        "tilOgMed": "2021-07-31",
                                        "type": "NY"
                                    },
                                    {
                                        "beløp":20946,
                                        "fraOgMed": "2021-08-01",
                                        "tilOgMed": "2021-12-31",
                                        "type": "NY"
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
                                          "tilOgMed":"2021-12-31"
                                        },
                                        "type":"SØKNAD",
                                        "harDokument": true
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
                                          "fraOgMed":"2021-05-01",
                                          "tilOgMed":"2021-07-31"
                                        },
                                        "type":"ENDRING",
                                        "harDokument": true
                                    },
                            ]
                            """.trimIndent(),
                            //language=JSON
                            expectedVedtakPåTidslinje = """
                                [
                                  {
                                        "periode":{
                                          "fraOgMed":"2021-01-01",
                                          "tilOgMed":"2021-04-30"
                                        },
                                        "vedtakType":"SØKNAD",
                                        "vedtakId": "ignore-me"
                                  },
                                  {
                                       "periode":{
                                          "fraOgMed":"2021-05-01",
                                          "tilOgMed":"2021-07-31"
                                        },
                                        "vedtakType":"ENDRING",
                                        "vedtakId": "ignore-me"
                                  },
                                  {
                                        "periode":{
                                          "fraOgMed":"2021-08-01",
                                          "tilOgMed":"2021-12-31"
                                        },
                                        "vedtakType":"SØKNAD",
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
