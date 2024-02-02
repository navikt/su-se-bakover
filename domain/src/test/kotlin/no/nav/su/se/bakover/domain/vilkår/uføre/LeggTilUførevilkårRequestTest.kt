package no.nav.su.se.bakover.domain.vilkår.uføre

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Test
import vilkår.uføre.domain.Uføregrad

internal class LeggTilUførevilkårRequestTest {

    @Test
    fun `Må sende inn uføregrad ved VilkårOppfylt`() {
        val behandlingId = RevurderingId.generer()
        LeggTilUførevilkårRequest(
            behandlingId = behandlingId,
            periode = januar(2021),
            uføregrad = null,
            forventetInntekt = 12000,
            oppfylt = UførevilkårStatus.VilkårOppfylt,
            begrunnelse = null,
        ).toVilkår(
            clock = fixedClock,
        ) shouldBe LeggTilUførevilkårRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler.left()
    }

    @Test
    fun `Må sende inn forventetInntekt ved VilkårOppfylt`() {
        val behandlingId = RevurderingId.generer()
        LeggTilUførevilkårRequest(
            behandlingId = behandlingId,
            periode = januar(2021),
            uføregrad = Uføregrad.parse(100),
            forventetInntekt = null,
            oppfylt = UførevilkårStatus.VilkårOppfylt,
            begrunnelse = null,
        ).toVilkår(
            clock = fixedClock,

        ) shouldBe LeggTilUførevilkårRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler.left()
    }

    @Test
    fun `Periode for vurdering og grunnlag må være lik`() {
        val behandlingId = RevurderingId.generer()
        LeggTilUførevilkårRequest(
            behandlingId = behandlingId,
            periode = januar(2021),
            uføregrad = Uføregrad.parse(100),
            forventetInntekt = null,
            oppfylt = UførevilkårStatus.VilkårOppfylt,
            begrunnelse = null,
        ).toVilkår(
            clock = fixedClock,

        ) shouldBe LeggTilUførevilkårRequest.UgyldigUførevurdering.UføregradOgForventetInntektMangler.left()
    }
}
