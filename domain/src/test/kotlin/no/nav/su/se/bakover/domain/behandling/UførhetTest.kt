package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class UførhetTest {

    @Test
    fun `er ikke ferdigbehandlet dersom status er har uføresak til behandlig`() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling,
            uføregrad = 100,
            forventetInntekt = 100,
            begrunnelse = null,
        ).let {
            it.erVilkårOppfylt() shouldBe false
            it.erVilkårIkkeOppfylt() shouldBe false
        }
    }

    @Test
    fun `vilkår er oppfylt dersom status er oppfylt`() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.VilkårOppfylt,
            uføregrad = 100,
            forventetInntekt = 100,
            begrunnelse = null,
        ).erVilkårOppfylt() shouldBe true
    }

    @Test
    fun `vilkår er ikke oppfylt dersom status er oppfylt`() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.VilkårIkkeOppfylt,
            uføregrad = 100,
            forventetInntekt = 100,
            begrunnelse = null,
        ).erVilkårOppfylt() shouldBe false
    }

    @Test
    fun `vilkår er oppfylt dersom status er uføresak til behandling`() {
        Behandlingsinformasjon.Uførhet(
            status = Behandlingsinformasjon.Uførhet.Status.HarUføresakTilBehandling,
            uføregrad = 100,
            forventetInntekt = 100,
            begrunnelse = null,
        ).erVilkårOppfylt() shouldBe false
    }
}
