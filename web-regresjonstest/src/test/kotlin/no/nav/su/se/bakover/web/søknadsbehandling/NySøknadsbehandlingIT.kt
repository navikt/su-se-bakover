package no.nav.su.se.bakover.web.søknadsbehandling

import no.nav.su.se.bakover.common.domain.tid.endOfMonth
import no.nav.su.se.bakover.common.domain.tid.startOfMonth
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.jwt.DEFAULT_IDENT
import no.nav.su.se.bakover.web.SharedRegressionTestData.withTestApplicationAndEmbeddedDb
import no.nav.su.se.bakover.web.sak.assertSakJson
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONObject
import org.junit.jupiter.api.Test

internal class NySøknadsbehandlingIT {

    @Test
    fun `ny innvilget søknadsbehandling uten eksisterende sak`() {
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
            assertSakJson(
                // 1. reservert, 2. kvittering søknadsbehandling
                expectedVersjon = 2,
                actualSakJson = hentSak(sakId, client = this.client),
                expectedFnr = fnr,
                expectedId = sakId,
                expectedUtbetalingerKanStansesEllerGjenopptas = "STANS",
                expectedBehandlinger = "[$opprettSøknadsbehandlingResponseJson]",
                expectedUtbetalinger = """
                    [
                        {
                         "beløp":10946,
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
                            "saksbehandler":"$DEFAULT_IDENT",
                            "utbetalingId":"ignore-me",
                            "behandlingId":"ignore-me",
                            "periode":{
                              "fraOgMed":"2021-01-01",
                              "tilOgMed":"2021-12-31"
                            },
                            "type":"SØKNAD",
                            "dokumenttilstand": "GENERERT",
                            "kanStarteNyBehandling": false,
                            "skalSendeBrev": true
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
