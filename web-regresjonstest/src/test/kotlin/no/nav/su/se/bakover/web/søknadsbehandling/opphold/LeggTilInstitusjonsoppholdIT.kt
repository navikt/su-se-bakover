package no.nav.su.se.bakover.web.søknadsbehandling.opphold

import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.søknad.ny.NySøknadJson
import no.nav.su.se.bakover.web.søknad.ny.nyDigitalSøknad
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.ny.startSøknadsbehandling
import no.nav.su.se.bakover.web.søknadsbehandling.virkningstidspunkt.leggTilStønadsperiode
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class LeggTilInstitusjonsoppholdIT {
    @Test
    fun `legg institusjonsopphold til søknadsbehandling`() {
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb {
            nyDigitalSøknad(client = this.client).also { nySøknadResponse ->
                val sakId = NySøknadJson.Response.hentSakId(nySøknadResponse)
                val søknadId = NySøknadJson.Response.hentSøknadId(nySøknadResponse)

                startSøknadsbehandling(
                    sakId = sakId,
                    søknadId = søknadId,
                    brukerrolle = Brukerrolle.Saksbehandler,
                    client = this.client,
                ).also { nyBehandlingResponse ->
                    val behandlingId = BehandlingJson.hentBehandlingId(nyBehandlingResponse)
                    val fraOgMed: String = 1.mai(2022).toString()
                    val tilOgMed: String = 31.desember(2022).toString()

                    leggTilStønadsperiode(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        client = this.client,
                    )

                    leggTilInstitusjonsopphold(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        vurdering = "VilkårOppfylt",
                        brukerrolle = Brukerrolle.Saksbehandler,
                        client = this.client,
                    ).also { søknadsbehandlingJson ->
                        JSONAssert.assertEquals(
                            JSONObject(BehandlingJson.hentInstitusjonsoppholdVilkår(søknadsbehandlingJson)).toString(),
                            //language=JSON
                            """
                                {
                                  "vurderingsperioder": [
                                    {
                                      "vurdering": "VilkårOppfylt",
                                      "periode": {
                                        "fraOgMed": "2022-05-01",
                                        "tilOgMed": "2022-12-31"
                                      }
                                    }
                                  ],
                                  "resultat": "VilkårOppfylt"
                                }
                            """.trimIndent(),
                            true,
                        )
                    }

                    leggTilInstitusjonsopphold(
                        sakId = sakId,
                        behandlingId = behandlingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        vurdering = "VilkårIkkeOppfylt",
                        brukerrolle = Brukerrolle.Saksbehandler,
                        client = this.client,
                    ).also { revurderingJson ->
                        JSONAssert.assertEquals(
                            JSONObject(BehandlingJson.hentInstitusjonsoppholdVilkår(revurderingJson)).toString(),
                            //language=JSON
                            """
                                {
                                  "vurderingsperioder": [
                                    {
                                      "vurdering": "VilkårIkkeOppfylt",
                                      "periode": {
                                        "fraOgMed": "2022-05-01",
                                        "tilOgMed": "2022-12-31"
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
