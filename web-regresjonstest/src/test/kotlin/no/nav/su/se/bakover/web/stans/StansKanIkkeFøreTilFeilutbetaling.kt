package no.nav.su.se.bakover.web.stans

import io.kotest.assertions.json.shouldEqualJson
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.domain.tid.februar
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.fixedClockAt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test

/**
 * Flyttet fra service-laget: StansAvYtelseServiceTest
 */
internal class StansKanIkkeFøreTilFeilutbetaling {

    @Test
    fun `kan ikke ha feilutbetaling i simulering`() {
        val tikkendeKlokke = TikkendeKlokke(fixedClockAt(31.januar(2021)))
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb(
            // Forventer at januar er utbetalt og derfor fører til feilutbetaling.
            clock = tikkendeKlokke,
            // Denne er ikke helt realistisk, siden utbetalingene skjer i løpet av måneden.
            // Så dersom klokka sier 31 januar, er januar allerede utbetalt og fører til feilutbetaling.
            utbetalingerKjørtTilOgMed = { 1.februar(2021) },
        ) { appComponents ->
            val fnr = Fnr.generer().toString()
            val førsteSøknadsbehandling = opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = "2021-01-01",
                tilOgMed = "2021-03-31",
                client = this.client,
                appComponents = appComponents,
            )
            val sakId = BehandlingJson.hentSakId(førsteSøknadsbehandling)
            val åpenStans = opprettStans(
                sakId = sakId,
                fraOgMed = "2021-01-01",
                client = this.client,
                expectedStatusCode = HttpStatusCode.BadRequest,
            )
            //language=json
            åpenStans shouldEqualJson """
                {
                "message":"Simulering fører til feilutbetaling.",
                "code":"simulering_fører_til_feilutbetaling"
                }
            """.trimIndent()
        }
    }

    @Test
    fun `kan ikke ha feilutbetaling i iverksetting`() {
        val tikkendeKlokke = TikkendeKlokke(fixedClockAt(31.januar(2021)))
        var utbetalingerKjørtTilOgMed = 1.januar(2021)
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb(
            // Forventer at januar er utbetalt og derfor fører til feilutbetaling.
            clock = tikkendeKlokke,
            // Denne er ikke helt realistisk, siden utbetalingene skjer i løpet av måneden.
            // Så dersom klokka sier 31 januar, er januar allerede utbetalt og fører til feilutbetaling.
            utbetalingerKjørtTilOgMed = { utbetalingerKjørtTilOgMed },
        ) { appComponents ->
            val fnr = Fnr.generer().toString()
            val førsteSøknadsbehandling = opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = "2021-01-01",
                tilOgMed = "2021-03-31",
                client = this.client,
                appComponents = appComponents,
            )
            val sakId = BehandlingJson.hentSakId(førsteSøknadsbehandling)
            val åpenStans = opprettStans(
                sakId = sakId,
                fraOgMed = "2021-01-01",
                client = this.client,
            )
            utbetalingerKjørtTilOgMed = 1.februar(2021)
            val iverksettStans = iverksettStans(
                sakId = sakId,
                behandlingId = RevurderingJson.hentRevurderingId(åpenStans),
                client = this.client,
                appComponents = appComponents,
                assertResponse = false,
            )
            // Siden januar nå er utbetalt, vil simuleringene være ulike.
            //language=json
            iverksettStans shouldEqualJson """
                {
                "message":"Kryssjekk av saksbehandlers og attestants simulering feilet - ulik verdi for feilutbetaling",
                "code":"kontrollsimulering_ulik_saksbehandlers_simulering"
                }
            """.trimIndent()
        }
    }
}
