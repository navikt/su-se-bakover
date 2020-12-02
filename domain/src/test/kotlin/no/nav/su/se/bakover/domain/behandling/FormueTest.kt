package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import org.junit.jupiter.api.Test

internal class FormueTest {

    @Test
    fun `er ikke ferdigbehandlet dersom status er må innhente er informasjon`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.MåInnhenteMerInformasjon,
            verdier = null,
            borSøkerMedEPS = false,
            epsVerdier = null,
            begrunnelse = "null"
        ).let {
            it.erVilkårOppfylt() shouldBe false
            it.erVilkårIkkeOppfylt() shouldBe false
        }
    }

    @Test
    fun `vilkår er oppfylt dersom status er oppfylt`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            verdier = null,
            borSøkerMedEPS = false,
            epsVerdier = null,
            begrunnelse = "null"
        ).erVilkårOppfylt() shouldBe true
    }

    @Test
    fun `vilkår er ikke oppfylt dersom status ikke er oppfylt`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårIkkeOppfylt,
            verdier = null,
            borSøkerMedEPS = false,
            epsVerdier = null,
            begrunnelse = "null"
        ).erVilkårOppfylt() shouldBe false

        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.MåInnhenteMerInformasjon,
            verdier = null,
            borSøkerMedEPS = false,
            epsVerdier = null,
            begrunnelse = "null"
        ).erVilkårOppfylt() shouldBe false
    }

    @Test
    fun `avslagsgrunn er formue dersom vilkår ikke er oppfylt`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårIkkeOppfylt,
            verdier = null,
            borSøkerMedEPS = false,
            epsVerdier = null,
            begrunnelse = "null"
        ).avslagsgrunn() shouldBe Avslagsgrunn.FORMUE
    }

    @Test
    fun `avslagsgrunn er null dersom vilkår er oppfylt`() {
        Behandlingsinformasjon.Formue(
            status = Behandlingsinformasjon.Formue.Status.VilkårOppfylt,
            verdier = null,
            borSøkerMedEPS = false,
            epsVerdier = null,
            begrunnelse = "null"
        ).avslagsgrunn() shouldBe null
    }
}
