package no.nav.su.se.bakover.web.revurdering.fastopphold

import no.nav.su.se.bakover.common.Brukerrolle
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.SharedRegressionTestData.fnr
import no.nav.su.se.bakover.web.revurdering.hentRevurderingId
import no.nav.su.se.bakover.web.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.fastopphold.avslåttFastOppholdJson
import no.nav.su.se.bakover.web.søknadsbehandling.fastopphold.innvilgetFastOppholdJson
import no.nav.su.se.bakover.web.søknadsbehandling.fastopphold.leggTilFastOppholdINorge
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class LeggTilFastOppholdINorgeIT {

    @Test
    fun `legg til fast opphold`() {
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb { appComponents ->
            opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
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

                    leggTilFastOppholdINorge(
                        sakId = sakId,
                        behandlingId = revurderingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        body = { innvilgetFastOppholdJson(fraOgMed, tilOgMed) },
                        brukerrolle = Brukerrolle.Saksbehandler,
                        url = "/saker/$sakId/revurderinger/$revurderingId/fastopphold",
                        client = this.client,
                    ).also { revurderingJson ->
                        JSONAssert.assertEquals(
                            JSONObject(RevurderingJson.hentFastOppholdVilkår(revurderingJson)).toString(),
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

                    leggTilFastOppholdINorge(
                        sakId = sakId,
                        behandlingId = revurderingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        body = { avslåttFastOppholdJson(fraOgMed, tilOgMed) },
                        brukerrolle = Brukerrolle.Saksbehandler,
                        url = "/saker/$sakId/revurderinger/$revurderingId/fastopphold",
                        client = this.client,
                    ).also { revurderingJson ->
                        JSONAssert.assertEquals(
                            JSONObject(RevurderingJson.hentFastOppholdVilkår(revurderingJson)).toString(),
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
