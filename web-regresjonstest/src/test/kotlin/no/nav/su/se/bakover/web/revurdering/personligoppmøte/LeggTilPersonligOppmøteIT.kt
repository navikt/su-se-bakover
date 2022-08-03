package no.nav.su.se.bakover.web.revurdering.personligoppmøte

import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.domain.Brukerrolle
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.SharedRegressionTestData.fnr
import no.nav.su.se.bakover.web.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson.hentRevurderingId
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.web.søknadsbehandling.personligoppmøte.avslåttPersonligOppmøteJson
import no.nav.su.se.bakover.web.søknadsbehandling.personligoppmøte.innvilgetPersonligOppmøteJson
import no.nav.su.se.bakover.web.søknadsbehandling.personligoppmøte.leggTilPersonligOppmøte
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class LeggTilPersonligOppmøteIT {
    @Test
    fun `legg til personlig oppmøte`() {
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb {
            opprettInnvilgetSøknadsbehandling(
                fnr = fnr,
                fraOgMed = 1.januar(2022).toString(),
                tilOgMed = 31.desember(2022).toString(),
            ).let { søknadsbehandlingJson ->

                val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)
                val fraOgMed = 1.mai(2022).toString()
                val tilOgMed = 31.desember(2022).toString()

                opprettRevurdering(
                    sakId = sakId,
                    fraOgMed = fraOgMed,
                ).let {
                    val revurderingId = hentRevurderingId(it)

                    leggTilPersonligOppmøte(
                        sakId = sakId,
                        behandlingId = revurderingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        body = { innvilgetPersonligOppmøteJson(fraOgMed, tilOgMed) },
                        brukerrolle = Brukerrolle.Saksbehandler,
                        url = "/saker/$sakId/revurderinger/$revurderingId/personligoppmøte",
                    ).also { revurderingJson ->
                        JSONAssert.assertEquals(
                            JSONObject(RevurderingJson.hentPersonligOppmøteVilkår(revurderingJson)).toString(),
                            //language=JSON
                            """
                                {
                                  "vurderinger": [
                                    {
                                      "resultat": "VilkårOppfylt",
                                      "vurdering": "MøttPersonlig",
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

                    leggTilPersonligOppmøte(
                        sakId = sakId,
                        behandlingId = revurderingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        body = { avslåttPersonligOppmøteJson(fraOgMed, tilOgMed) },
                        brukerrolle = Brukerrolle.Saksbehandler,
                        url = "/saker/$sakId/revurderinger/$revurderingId/personligoppmøte",
                    ).also { revurderingJson ->
                        JSONAssert.assertEquals(
                            JSONObject(RevurderingJson.hentPersonligOppmøteVilkår(revurderingJson)).toString(),
                            //language=JSON
                            """
                                {
                                  "vurderinger": [
                                    {
                                      "resultat": "VilkårIkkeOppfylt",
                                      "vurdering": "IkkeMøttPersonlig",
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
