package no.nav.su.se.bakover.web.stans

import io.kotest.assertions.json.shouldEqualJson
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test

/**
 * Kan ikke iverksette en stans, dersom det har kommet et søknadsbehandlingsvedtak etter vi simulerte stansen.
 * Sjekke o implisitt at vi kan opprette en søknadsbehandling og en stans samtidig.
 */
internal class SamtidigGrunnlagsdataOgVilkårsvurderingerSøknadsbehandlingOgStansIT {
    @Test
    fun `ny innvilget søknadsbehandling uten eksisterende sak`() {
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb(
            clock = TikkendeKlokke(),
        ) { appComponents ->
            val fnr = Fnr.generer().toString()
            val førsteSøknadsbehandling = opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = "2021-01-01",
                tilOgMed = "2021-12-31",
                client = this.client,
                appComponents = appComponents,
            )
            val sakId = BehandlingJson.hentSakId(førsteSøknadsbehandling)
            val åpenStans = opprettStans(
                sakId = sakId,
                fraOgMed = "2021-01-01",
                client = this.client,
            )
            opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = "2022-01-01",
                tilOgMed = "2022-12-31",
                client = this.client,
                appComponents = appComponents,
            )
            val iverksettStans = iverksettStans(
                sakId = sakId,
                behandlingId = RevurderingJson.hentRevurderingId(åpenStans),
                client = this.client,
                appComponents = appComponents,
                assertResponse = false,
            )
            //language=json
            iverksettStans shouldEqualJson """
                {
                "message":"Det har kommet nye vedtak i denne revurderingsperioden etter at denne revurderingen ble opprettet eller oppdatert.",
                "code":"nye_overlappende_vedtak"
                }
            """.trimIndent()
        }
    }
}
