package no.nav.su.se.bakover.web.søknadsbehandling

import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.SharedRegressionTestData.withTestApplicationAndEmbeddedDb
import no.nav.su.se.bakover.web.sak.assertSakJson
import no.nav.su.se.bakover.web.sak.hent.hentSak
import org.json.JSONObject
import org.junit.jupiter.api.Test

/**
 * Skal simulere at en veileder sender inn en søknad for en person som ikke har en sak fra før.
 *
 * TODO jah: Sjekk opp om det er noen praktisk forskjell rundt dette, eller om det er personen som styrer dette.
 */
internal class NySøknadsbehandlingIT {

    @Test
    fun `ny søknadsbehandling`() {
        withTestApplicationAndEmbeddedDb() {
            val fnr = Fnr.generer().toString()
            val opprettSøknadsbehandlingResponseJson = opprettInnvilgetSøknadsbehandling(fnr = fnr)
            val sakId = BehandlingJson.hentSakId(opprettSøknadsbehandlingResponseJson)
            assertSakJson(
                actualSakJson = hentSak(sakId).also {
                    println(it)
                },
                expectedFnr = fnr,
                expectedId = sakId,
                expectedUtbetalingerKanStansesEllerGjenopptas = "STANS",
                expectedBehandlinger = "[$opprettSøknadsbehandlingResponseJson]",
                expectedUtbetalinger = """
                    [
                        {
                         "beløp":21989,
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
                              "fraOgMed":"2022-02-01",
                              "tilOgMed":"2023-01-31"
                            },
                            "type":"SØKNAD"
                        }
                    ]
                """.trimIndent(),
            )
        }
    }
}
