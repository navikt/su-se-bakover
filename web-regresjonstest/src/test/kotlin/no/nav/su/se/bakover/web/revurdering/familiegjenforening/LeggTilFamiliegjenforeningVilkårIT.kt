package no.nav.su.se.bakover.web.revurdering.familiegjenforening

import no.nav.su.se.bakover.client.stubs.person.PersonOppslagStub
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.desember
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.mai
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.FamiliegjenforeningvilkårStatus
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.web.SharedRegressionTestData
import no.nav.su.se.bakover.web.revurdering.hentRevurderingId
import no.nav.su.se.bakover.web.revurdering.opprett.opprettRevurdering
import no.nav.su.se.bakover.web.routes.vilkår.FamiliegjenforeningVilkårJson
import no.nav.su.se.bakover.web.routes.vilkår.VurderingsperiodeFamiliegjenforeningJson
import no.nav.su.se.bakover.web.søknadsbehandling.BehandlingJson
import no.nav.su.se.bakover.web.søknadsbehandling.RevurderingJson
import no.nav.su.se.bakover.web.søknadsbehandling.familiegjenforening.leggTilFamiliegjenforening
import no.nav.su.se.bakover.web.søknadsbehandling.opprettInnvilgetSøknadsbehandling
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
                    informasjonSomRevurderes = serialize(listOf("Familiegjenforening")),
                ).let {
                    val revurderingId = hentRevurderingId(it)

                    leggTilFamiliegjenforening(
                        sakId = sakId,
                        behandlingId = revurderingId,
                        resultat = FamiliegjenforeningvilkårStatus.VilkårOppfylt,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        brukerrolle = Brukerrolle.Saksbehandler,
                        url = "/saker/$sakId/revurderinger/$revurderingId/familiegjenforening",
                        client = this.client,
                    ).also { revurderingJson ->
                        assertEquals(
                            FamiliegjenforeningVilkårJson(
                                vurderinger = listOf(
                                    VurderingsperiodeFamiliegjenforeningJson(
                                        periode = PeriodeJson(
                                            fraOgMed = "2022-05-01",
                                            tilOgMed = "2022-12-31",
                                        ),
                                        status = FamiliegjenforeningvilkårStatus.VilkårOppfylt,
                                    ),
                                ),
                            ),
                            deserialize(RevurderingJson.hentFamiliegjenforeningVilkår(revurderingJson)),
                        )
                    }

                    leggTilFamiliegjenforening(
                        sakId = sakId,
                        behandlingId = revurderingId,
                        resultat = FamiliegjenforeningvilkårStatus.VilkårIkkeOppfylt,
                        fraOgMed = fraOgMed,
                        tilOgMed = tilOgMed,
                        brukerrolle = Brukerrolle.Saksbehandler,
                        url = "/saker/$sakId/revurderinger/$revurderingId/familiegjenforening",
                        client = this.client,
                    ).also { revurderingJson ->

                        assertEquals(
                            FamiliegjenforeningVilkårJson(
                                vurderinger = listOf(
                                    VurderingsperiodeFamiliegjenforeningJson(
                                        periode = PeriodeJson(
                                            fraOgMed = "2022-05-01",
                                            tilOgMed = "2022-12-31",
                                        ),
                                        status = FamiliegjenforeningvilkårStatus.VilkårIkkeOppfylt,
                                    ),
                                ),
                            ),
                            deserialize(RevurderingJson.hentFamiliegjenforeningVilkår(revurderingJson)),
                        )
                    }
                }
            }
        }
    }
}
