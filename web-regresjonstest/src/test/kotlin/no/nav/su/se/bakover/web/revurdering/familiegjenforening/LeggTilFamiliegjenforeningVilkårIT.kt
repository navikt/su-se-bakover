package no.nav.su.se.bakover.web.revurdering.familiegjenforening

import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.revurdering.hentRevurderingId
import no.nav.su.se.bakover.web.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.familiegjenforening.leggTilFamiliegjenforening
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert

class LeggTilFamiliegjenforeningVilkårIT {
    @Test
    fun `legg til vilkår familiegjenforening`() {
        SharedRegressionTestData.withTestApplicationAndEmbeddedDb(
            personOppslagStub = PersonOppslagStub(fødselsdato = 1.januar(1955)),
        ) { appComponents ->
            opprettInnvilgetSøknadsbehandling(
                fnr = fnr.toString(),
                fraOgMed = 1.januar(2022).toString(),
                tilOgMed = 31.desember(2022).toString(),
                client = this.client,
                appComponents = appComponents,
                sakstype = Sakstype.ALDER,
            ).let { søknadsbehandlingJson ->
                val sakId = BehandlingJson.hentSakId(søknadsbehandlingJson)
                val fraOgMed = 1.mai(2022).toString()
                val tilOgMed = 31.desember(2022).toString()

                opprettRevurdering(
                    sakId = sakId,
                    fraOgMed = fraOgMed,
                    tilOgMed = tilOgMed,
                    client = this.client,
                    informasjonSomRevurderes = """
                        [
                            "Familiegjenforening"
                        ]
                    """.trimIndent(),
                ).let {
                    val revurderingId = hentRevurderingId(it)

                    leggTilFamiliegjenforening(
                        sakId = sakId,
                        behandlingId = revurderingId,
                        resultat = "VilkårOppfylt",
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        brukerrolle = Brukerrolle.Saksbehandler,
                        url = "/saker/$sakId/revurderinger/$revurderingId/familiegjenforening",
                        client = this.client,
                    ).also { revurderingJson ->
                        JSONAssert.assertEquals(
                            JSONObject(RevurderingJson.hentFamiliegjenforeningVilkår(revurderingJson)).toString(),
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

                    leggTilFamiliegjenforening(
                        sakId = sakId,
                        behandlingId = revurderingId,
                        resultat = "VilkårIkkeOppfylt",
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        brukerrolle = Brukerrolle.Saksbehandler,
                        url = "/saker/$sakId/revurderinger/$revurderingId/familiegjenforening",
                        client = this.client,
                    ).also { revurderingJson ->
                        JSONAssert.assertEquals(
                            JSONObject(RevurderingJson.hentFamiliegjenforeningVilkår(revurderingJson)).toString(),
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
