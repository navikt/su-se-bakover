package no.nav.su.se.bakover.web.revurdering.flyktning

import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.revurdering.hentRevurderingId
import no.nav.su.se.bakover.web.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.avslåttFlyktningVilkårJson
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.innvilgetFlyktningVilkårJson
import no.nav.su.se.bakover.web.søknadsbehandling.flyktning.leggTilFlyktningVilkår
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class LeggTilFlyktningVilkårIT {
    @Test
    fun `legg til flyktningvilkår`() {
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb { appComponents ->
            opprettInnvilgetSøknadsbehandling(
                fnr = fnr.toString(),
                fraOgMed = 1.januar(2022).toString(),
                tilOgMed = 31.desember(2022).toString(),
                client = this.client,
                appComponents = appComponents,
            ).let { søknadsbehandlingJson ->

                val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)
                val fraOgMed = 1.mai(2022).toString()
                val tilOgMed = 31.desember(2022).toString()

                opprettRevurdering(
                    sakId = sakId,
                    fraOgMed = fraOgMed,
                    tilOgMed = tilOgMed,
                    client = this.client,
                ).let {
                    val revurderingId = hentRevurderingId(it)

                    leggTilFlyktningVilkår(
                        sakId = sakId,
                        behandlingId = revurderingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        body = { innvilgetFlyktningVilkårJson(fraOgMed, tilOgMed) },
                        brukerrolle = Brukerrolle.Saksbehandler,
                        url = "/saker/$sakId/revurderinger/$revurderingId/flyktning",
                        client = this.client,
                    ).also { revurderingJson ->
                        JSONAssert.assertEquals(
                            JSONObject(RevurderingJson.hentFlyktningVilkår(revurderingJson)).toString(),
                            //language=JSON
                            """
                                {
                                  "vurderinger": [
                                    {
                                      "resultat": "VilkårOppfylt",
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

                    leggTilFlyktningVilkår(
                        sakId = sakId,
                        behandlingId = revurderingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        body = { avslåttFlyktningVilkårJson(fraOgMed, tilOgMed) },
                        brukerrolle = Brukerrolle.Saksbehandler,
                        url = "/saker/$sakId/revurderinger/$revurderingId/flyktning",
                        client = this.client,
                    ).also { revurderingJson ->
                        JSONAssert.assertEquals(
                            JSONObject(RevurderingJson.hentFlyktningVilkår(revurderingJson)).toString(),
                            //language=JSON
                            """
                                {
                                  "vurderinger": [
                                    {
                                      "resultat": "VilkårIkkeOppfylt",
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
