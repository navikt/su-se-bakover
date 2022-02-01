package no.nav.su.se.bakover.web.søknadsbehandling.ny

import no.nav.su.se.bakover.database.withMigratedDb
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.fnr
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknadOgVerifiser
import no.nav.su.se.bakover.web.søknadsbehandling.assertSøknadsbehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.behandlingsinformasjon.tomBehandlingsinformasjonResponse
import no.nav.su.se.bakover.web.søknadsbehandling.grunnlagsdataOgVilkårsvurderinger.tomGrunnlagsdataOgVilkårsvurderingerResponse
import org.json.JSONObject
import org.junit.jupiter.api.Test

internal class NySøknadsbehandlingIT {

    @Test
    fun `Kan opprette ny søkndsbehandling`() {
        val fnr = fnr
        withMigratedDb { dataSource ->
            val nySøknadsrespons = nyDigitalSøknadOgVerifiser(
                fnr = fnr,
                expectedSaksnummerInResponse = 2021,
                dataSource = dataSource,
            )
            val sakId = NySøknadJson.Response.hentSakId(nySøknadsrespons)
            val søknadId = NySøknadJson.Response.hentSøknadId(nySøknadsrespons)
            nySøknadsbehandling(
                sakId = sakId,
                søknadId = søknadId,
                brukerrolle = Brukerrolle.Saksbehandler,
                dataSource = dataSource,
            ).also { actual ->
                assertSøknadsbehandlingJson(
                    actualSøknadsbehandlingJson = actual,
                    expectedBehandlingsinformasjon = tomBehandlingsinformasjonResponse(),
                    expectedSøknad = JSONObject(nySøknadsrespons).getJSONObject("søknad").toString(),
                    expectedSakId = sakId,
                    expectedGrunnlagsdataOgVilkårsvurderinger = tomGrunnlagsdataOgVilkårsvurderingerResponse(),
                )
            }
        }
    }
}
