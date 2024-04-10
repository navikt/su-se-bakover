package no.nav.su.se.bakover.web.søknadsbehandling.flyktning

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.sak.assertSakJson
import no.nav.su.se.bakover.web.sak.hent.hentSak
import no.nav.su.se.bakover.web.søknad.digitalUføreSøknadJson
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknad
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.ny.nySøknadsbehandling
import no.nav.su.se.bakover.web.søknadsbehandling.virkningstidspunkt.leggTilStønadsperiode
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class LeggTilFlyktningVilkårIT {
    @Test
    fun `legg til flyktningvilkår`() {
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb {
            nyDigitalSøknad(client = this.client).also { nySøknadResponse ->
                val sakId = NySøknadJson.Response.hentSakId(nySøknadResponse)
                val sakJson = hentSak(sakId, this.client)
                val søknadId = NySøknadJson.Response.hentSøknadId(nySøknadResponse)

                assertSakJson(
                    actualSakJson = sakJson,
                    expectedSaksnummer = 2021,
                    expectedSøknader = "[${
                        digitalUføreSøknadJson(
                            SharedRegressionTestData.fnr,
                            SharedRegressionTestData.epsFnr,
                        )
                    }]",
                    expectedSakstype = "uføre",
                )

                nySøknadsbehandling(
                    sakId = sakId,
                    søknadId = søknadId,
                    brukerrolle = Brukerrolle.Saksbehandler,
                    client = this.client,
                ).also { nyBehandlingResponse ->
                    val behandlingId = BehandlingJson.hentBehandlingId(nyBehandlingResponse)
                    val fraOgMed: String = 1.januar(2022).toString()
                    val tilOgMed: String = 31.desember(2022).toString()

                    leggTilStønadsperiode(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        client = this.client,
                    )

                    leggTilFlyktningVilkår(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        body = { innvilgetFlyktningVilkårJson(fraOgMed, tilOgMed) },
                        brukerrolle = Brukerrolle.Saksbehandler,
                        url = "/saker/$sakId/behandlinger/$behandlingId/flyktning",
                        client = this.client,
                    ).also { behandlingJson ->
                        JSONAssert.assertEquals(
                            JSONObject(BehandlingJson.hentFlyktningVilkår(behandlingJson)).toString(),
                            //language=JSON
                            """
                                {
                                  "vurderinger": [
                                    {
                                      "resultat": "VilkårOppfylt",
                                      "periode": {
                                        "fraOgMed": "2022-01-01",
                                        "tilOgMed": "2022-12-31"
                                      }
                                    }
                                  ],
                                  "resultat": "VilkårOppfylt"
                                }
                            """.trimIndent(),
                            true,
                        )
                        BehandlingJson.hentStatus(behandlingJson) shouldBe "OPPRETTET"
                    }

                    leggTilFlyktningVilkår(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        body = { avslåttFlyktningVilkårJson(fraOgMed, tilOgMed) },
                        brukerrolle = Brukerrolle.Saksbehandler,
                        url = "/saker/$sakId/behandlinger/$behandlingId/flyktning",
                        client = this.client,
                    ).also { behandlingJson ->
                        JSONAssert.assertEquals(
                            JSONObject(BehandlingJson.hentFlyktningVilkår(behandlingJson)).toString(),
                            //language=JSON
                            """
                                {
                                  "vurderinger": [
                                    {
                                      "resultat": "VilkårIkkeOppfylt",
                                      "periode": {
                                        "fraOgMed": "2022-01-01",
                                        "tilOgMed": "2022-12-31"
                                      }
                                    }
                                  ],
                                  "resultat": "VilkårIkkeOppfylt"
                                }
                            """.trimIndent(),
                            true,
                        )
                        BehandlingJson.hentStatus(behandlingJson) shouldBe "VILKÅRSVURDERT_AVSLAG"
                    }
                }
            }
        }
    }
}
