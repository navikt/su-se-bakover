package no.nav.su.se.bakover.web.søknadsbehandling.ny

import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData.fnr
import no.nav.su.se.bakover.web.SharedRegressionTestData.withTestApplicationAndEmbeddedDb
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknadOgVerifiser
import no.nav.su.se.bakover.web.søknadsbehandling.assertSøknadsbehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.grunnlagsdataOgVilkårsvurderinger.tomGrunnlagsdataOgVilkårsvurderingerResponse
import org.json.JSONObject
import org.junit.jupiter.api.Test

internal class NyGrunnlagsdataOgVilkårsvurderingerSøknadsbehandlingIT {

    @Test
    fun `Kan opprette ny søknadsbehandling for uføre`() {
        val fnr = fnr
        withTestApplicationAndEmbeddedDb {
            val nySøknadsrespons = nyDigitalSøknadOgVerifiser(
                fnr = fnr,
                expectedSaksnummerInResponse = 2021,
            )
            val sakId = NySøknadJson.Response.hentSakId(nySøknadsrespons)
            val søknadId = NySøknadJson.Response.hentSøknadId(nySøknadsrespons)
            nySøknadsbehandling(
                sakId = sakId,
                søknadId = søknadId,
                brukerrolle = Brukerrolle.Saksbehandler,
                client = this.client,
            ).also { actual ->
                assertSøknadsbehandlingJson(
                    actualSøknadsbehandlingJson = actual,
                    expectedSøknad = JSONObject(nySøknadsrespons).getJSONObject("søknad").toString(),
                    expectedSakId = sakId,
                    expectedGrunnlagsdataOgVilkårsvurderinger = tomGrunnlagsdataOgVilkårsvurderingerResponse(),
                    expectedSaksbehandler = "Z990Lokal",
                )
            }
        }
    }
}
