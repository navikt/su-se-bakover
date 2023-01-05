package no.nav.su.se.bakover.web.søknadsbehandling.pensjon

import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.avslåttPensjonsvilkårJson
import no.nav.su.se.bakover.web.innvilgetPensjonsvilkårJson
import no.nav.su.se.bakover.web.leggTilPensjonsVilkår
import no.nav.su.se.bakover.web.sak.assertSakJson
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.søknad.digitalAlderSøknadJson
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalAlderssøknad
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.assertSøknadsbehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.grunnlagsdataOgVilkårsvurderinger.tomGrunnlagsdataOgVilkårsvurderingerResponse
import no.nav.su.se.bakover.web.søknadsbehandling.ny.nySøknadsbehandling
import no.nav.su.se.bakover.web.søknadsbehandling.virkningstidspunkt.leggTilVirkningstidspunkt
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class LeggTilPensjonsVilkårIT {
    @Test
    fun `legg til pensjonsvilkår`() {
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb {
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

                    leggTilVirkningstidspunkt(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        client = this.client,
                    )

                    leggTilPensjonsVilkår(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        body = { innvilgetPensjonsvilkårJson(fraOgMed, tilOgMed) },
                        brukerrolle = Brukerrolle.Saksbehandler,
                        client = this.client,
                    ).also {
                        JSONAssert.assertEquals(
                            JSONObject(BehandlingJson.hentPensjonsVilkår(it)).toString(),
                            //language=JSON
                            """
                                {
                                  "vurderinger": [
                                    {
                                      "resultat": "VilkårOppfylt",
                                      "periode": {
                                        "fraOgMed": "2022-01-01",
                                        "tilOgMed": "2022-12-31"
                                      },
                                      "pensjonsopplysninger": {
                                        "folketrygd": "JA",
                                        "andreNorske": "IKKE_AKTUELT",
                                        "utenlandske": "JA"
                                      }
                                    }
                                  ],
                                  "resultat": "VilkårOppfylt"
                                }
                            """.trimIndent(),
                            true,
                        )
                    }

                    leggTilPensjonsVilkår(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        body = { avslåttPensjonsvilkårJson(fraOgMed, tilOgMed) },
                        brukerrolle = Brukerrolle.Saksbehandler,
                        client = this.client,
                    ).also {
                        JSONAssert.assertEquals(
                            JSONObject(BehandlingJson.hentPensjonsVilkår(it)).toString(),
                            //language=JSON
                            """
                                {
                                  "vurderinger": [
                                    {
                                      "resultat": "VilkårIkkeOppfylt",
                                      "periode": {
                                        "fraOgMed": "2022-01-01",
                                        "tilOgMed": "2022-12-31"
                                      },
                                      "pensjonsopplysninger": {
                                        "folketrygd": "NEI",
                                        "andreNorske": "IKKE_AKTUELT",
                                        "utenlandske": "JA"
                                      }
                                    }
                                  ],
                                  "resultat": "VilkårIkkeOppfylt"
                                }
                            """.trimIndent(),
                            true,
                        )
                    }
                }
            }
        }
    }
}
