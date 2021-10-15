package no.nav.su.se.bakover.service.vilkår

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test
import java.util.UUID

internal class LeggTilUførevurderingRequestTest {

    @Test
    fun `vurderingsperioden kan ikke være utenfor behandlingsperioden`() {
        val behandlingId = UUID.randomUUID()
        LeggTilUførevurderingRequest(
            behandlingId = behandlingId,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 28.februar(2021)),
            uføregrad = Uføregrad.parse(100),
            forventetInntekt = 12000,
            oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
            begrunnelse = null,
        ).toVilkår(
            Periode.create(
                fraOgMed = 1.februar(2021),
                tilOgMed = 28.februar(2021),
            ),
            clock = fixedClock,
        ) shouldBe LeggTilUførevurderingRequest.UgyldigUførevurdering.VurderingsperiodenKanIkkeVæreUtenforBehandlingsperioden.left()
    }

    @Test
    fun `Må sende inn uføregrad ved VilkårOppfylt`() {
        val behandlingId = UUID.randomUUID()
        LeggTilUførevurderingRequest(
            behandlingId = behandlingId,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
            uføregrad = null,
            forventetInntekt = 12000,
            oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
            begrunnelse = null,
        ).toVilkår(
            Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.januar(2021),
            ),
            clock = fixedClock,
        ) shouldBe LeggTilUførevurderingRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler.left()
    }

    @Test
    fun `Må sende inn forventetInntekt ved VilkårOppfylt`() {
        val behandlingId = UUID.randomUUID()
        LeggTilUførevurderingRequest(
            behandlingId = behandlingId,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
            uføregrad = Uføregrad.parse(100),
            forventetInntekt = null,
            oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
            begrunnelse = null,
        ).toVilkår(
            Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.januar(2021),
            ),
            clock = fixedClock,

        ) shouldBe LeggTilUførevurderingRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler.left()
    }

    @Test
    fun `Periode for vurdering og grunnlag må være lik`() {
        val behandlingId = UUID.randomUUID()
        LeggTilUførevurderingRequest(
            behandlingId = behandlingId,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.januar(2021)),
            uføregrad = Uføregrad.parse(100),
            forventetInntekt = null,
            oppfylt = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
            begrunnelse = null,
        ).toVilkår(
            Periode.create(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.januar(2021),
            ),
            clock = fixedClock,

        ) shouldBe LeggTilUførevurderingRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler.left()
    }
}
