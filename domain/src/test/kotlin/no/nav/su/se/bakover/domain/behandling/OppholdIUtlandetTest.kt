package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class OppholdIUtlandetTest {

    @Test
    fun `er ikke ferdigbehandlet hvis status er uavklart`() {
        Behandlingsinformasjon.OppholdIUtlandet(
            status = Behandlingsinformasjon.OppholdIUtlandet.Status.Uavklart,
            begrunnelse = "neh"
        ).let {
            it.erVilkårOppfylt() shouldBe false
            it.erVilkårIkkeOppfylt() shouldBe false
        }
    }

    @Test
    fun `er oppfylt hvis status er skal holde seg i norge`() {
        Behandlingsinformasjon.OppholdIUtlandet(
            status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
            begrunnelse = null
        ).erVilkårOppfylt() shouldBe true
    }

    @Test
    fun `er ikke oppfylt hvis status er skal være i utlandet`() {
        Behandlingsinformasjon.OppholdIUtlandet(
            status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalVæreMerEnn90DagerIUtlandet,
            begrunnelse = null
        ).erVilkårOppfylt() shouldBe false
    }
}
