package no.nav.su.se.bakover.web.revurdering.institusjonsopphold

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
import no.nav.su.se.bakover.web.søknadsbehandling.opphold.leggTilInstitusjonsopphold
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

internal class LeggTilInstitusjonsoppholdIT {
    @Test
    fun `legg til institusjonsopphold`() {
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

                    leggTilInstitusjonsopphold(
                        sakId = sakId,
                        behandlingId = revurderingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        vurdering = "VilkårOppfylt",
                        url = "/saker/$sakId/revurderinger/$revurderingId/institusjonsopphold",
                        brukerrolle = Brukerrolle.Saksbehandler,
                    ).also { revurderingJson ->
                        JSONAssert.assertEquals(
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
                            JSONObject(RevurderingJson.hentInstitusjonsoppholdVilkår(revurderingJson)).toString(),
                            true,
                        )
                    }

                    leggTilInstitusjonsopphold(
                        sakId = sakId,
                        behandlingId = revurderingId,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        vurdering = "VilkårIkkeOppfylt",
                        url = "/saker/$sakId/revurderinger/$revurderingId/institusjonsopphold",
                        brukerrolle = Brukerrolle.Saksbehandler,
                    ).also { revurderingJson ->
                        JSONAssert.assertEquals(
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
                            JSONObject(RevurderingJson.hentInstitusjonsoppholdVilkår(revurderingJson)).toString(),
                            true,
                        )
                    }
                }
            }
        }
    }
}
