package no.nav.su.se.bakover.web.søknadsbehandling

import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.SharedRegressionTestData.withTestApplicationAndEmbeddedDb
import no.nav.su.se.bakover.web.sak.assertSakJson
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONObject
import org.junit.jupiter.api.Test

internal class NySøknadsbehandlingIT {

    @Test
    fun `ny innvilget søknadsbehandling uten eksisterende sak`() {
        withTestApplicationAndEmbeddedDb {
            val fnr = Fnr.generer().toString()
            val opprettSøknadsbehandlingResponseJson = opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = fixedLocalDate.startOfMonth().toString(),
                tilOgMed = fixedLocalDate.plusMonths(11).endOfMonth().toString(),
            )
            val sakId = BehandlingJson.hentSakId(opprettSøknadsbehandlingResponseJson)
            assertSakJson(
                actualSakJson = hentSak(sakId),
                expectedFnr = fnr,
                expectedId = sakId,
                expectedUtbetalingerKanStansesEllerGjenopptas = "STANS",
                expectedBehandlinger = "[$opprettSøknadsbehandlingResponseJson]",
                expectedUtbetalinger = """
                    [
                        {
                         "beløp":20946,
                         "fraOgMed":"2021-01-01",
                         "tilOgMed":"2021-12-31",
                         "type":"NY"
                        }
                    ]
                """.trimIndent(),
                expectedSøknader = """
                    [
                    ${JSONObject(opprettSøknadsbehandlingResponseJson).getJSONObject("søknad")}
                    ]
                """.trimIndent(),
                expectedVedtak = """
                    [
                        {
                            "id":"ignore-me",
                            "opprettet":"2021-01-01T01:02:03.456789Z",
                            "beregning":${JSONObject(opprettSøknadsbehandlingResponseJson).getJSONObject("beregning")},
                            "simulering":${JSONObject(opprettSøknadsbehandlingResponseJson).getJSONObject("simulering")},
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
                        }
                    ]
                """.trimIndent(),
                // language=JSON
                expectedVedtakPåTidslinje = """
                    [
                        {
                            "vedtakType": "SØKNAD",
                            "periode":{
                              "fraOgMed":"2021-01-01",
                              "tilOgMed":"2021-12-31"
                            },
                            "vedtakId": "ignore-me"
                        }
                    ]
                """.trimIndent(),
            )
        }
    }
}
