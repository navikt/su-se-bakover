package no.nav.su.se.bakover.web.søknadsbehandling.familiegjenforening

import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.FamiliegjenforeningvilkårStatus
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.routes.vilkår.FamiliegjenforeningVilkårRequest
import no.nav.su.se.bakover.web.routes.vilkår.VurderingsperiodeFamiliegjenforeningJson
import no.nav.su.se.bakover.web.sak.assertSakJson
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.søknad.digitalAlderSøknadJson
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalAlderssøknad
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.assertSøknadsbehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.grunnlagsdataOgVilkårsvurderinger.tomGrunnlagsdataOgVilkårsvurderingerResponse
import no.nav.su.se.bakover.web.søknadsbehandling.ny.nySøknadsbehandling
import no.nav.su.se.bakover.web.søknadsbehandling.virkningstidspunkt.leggTilStønadsperiode
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LeggTilFamiliegjenforeningIT {

    @Test
    fun `legg pensjonsvilkår til søknadsbehandling `() {
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb(personOppslagStub = PersonOppslagStub(fødselsdato = 1.januar(1954))) {
            nyDigitalAlderssøknad(client = this.client).also { nySøknadResponse ->
                val sakId = NySøknadJson.Response.hentSakId(nySøknadResponse)
                val sakJson = hentSak(sakId, this.client)
                val søknadId = NySøknadJson.Response.hentSøknadId(nySøknadResponse)

                assertSakJson(
                    actualSakJson = sakJson,
                    expectedSaksnummer = 2021,
                    expectedSøknader = "[${
                        digitalAlderSøknadJson(
                            SharedRegressionTestData.fnr,
                            SharedRegressionTestData.epsFnr,
                        )
                    }]",
                    expectedSakstype = "alder",
                )

                nySøknadsbehandling(
                    sakId = sakId,
                    søknadId = søknadId,
                    brukerrolle = Brukerrolle.Saksbehandler,
                    client = this.client,
                ).also { nyBehandlingResponse ->
                    val behandlingId = BehandlingJson.hentBehandlingId(nyBehandlingResponse)

                    assertSøknadsbehandlingJson(
                        actualSøknadsbehandlingJson = nyBehandlingResponse,
                        expectedSøknad = JSONObject(nyBehandlingResponse).getJSONObject("søknad").toString(),
                        expectedSakId = sakId,
                        expectedGrunnlagsdataOgVilkårsvurderinger = tomGrunnlagsdataOgVilkårsvurderingerResponse(),
                        expectedSakstype = "alder",
                        expectedSaksbehandler = "Z990Lokal",
                    )

                    val fraOgMed: String = 1.januar(2022).toString()
                    val tilOgMed: String = 31.desember(2022).toString()

                    leggTilStønadsperiode(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        client = this.client,
                    )

                    leggTilFamiliegjenforening(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        resultat = FamiliegjenforeningvilkårStatus.VilkårOppfylt,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        brukerrolle = Brukerrolle.Saksbehandler,
                        client = this.client,
                    ).also { behandlingJson ->
                        assertEquals(
                            FamiliegjenforeningVilkårRequest(
                                vurderinger = listOf(
                                    VurderingsperiodeFamiliegjenforeningJson(
                                        periode = PeriodeJson(
                                            fraOgMed = "2022-01-01",
                                            tilOgMed = "2022-12-31",
                                        ),
                                        resultat = FamiliegjenforeningvilkårStatus.VilkårOppfylt,
                                    ),
                                ),
                            ),
                            deserialize(BehandlingJson.hentFamiliegjenforeningVilkår(behandlingJson)),
                        )
                    }

                    leggTilFamiliegjenforening(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        resultat = FamiliegjenforeningvilkårStatus.VilkårIkkeOppfylt,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        brukerrolle = Brukerrolle.Saksbehandler,
                        client = this.client,
                    ).also { behandlingJson ->
                        assertEquals(
                            FamiliegjenforeningVilkårRequest(
                                vurderinger = listOf(
                                    VurderingsperiodeFamiliegjenforeningJson(
                                        periode = PeriodeJson(
                                            fraOgMed = "2022-01-01",
                                            tilOgMed = "2022-12-31",
                                        ),
                                        resultat = FamiliegjenforeningvilkårStatus.VilkårIkkeOppfylt,
                                    ),
                                ),
                            ),
                            deserialize(BehandlingJson.hentFamiliegjenforeningVilkår(behandlingJson)),
                        )
                    }
                }
            }
        }
    }
}
