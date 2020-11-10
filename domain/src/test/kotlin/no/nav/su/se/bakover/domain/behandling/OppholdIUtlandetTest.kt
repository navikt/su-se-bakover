package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.brev.Avslagsgrunn
import org.junit.jupiter.api.Test

internal class OppholdIUtlandetTest {
    @Test
    fun `er gyldig uansett hva man putter inn`() {
        Behandlingsinformasjon.OppholdIUtlandet(
            status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
            begrunnelse = "neh"
        ).erGyldig() shouldBe true

        Behandlingsinformasjon.OppholdIUtlandet(
            status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalVæreMerEnn90DagerIUtlandet,
            begrunnelse = null
        ).erGyldig() shouldBe true
    }

    @Test
    fun `er ferdigbehandlet uansett hva man putter inn`() {
        Behandlingsinformasjon.OppholdIUtlandet(
            status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
            begrunnelse = "neh"
        ).erFerdigbehandlet() shouldBe true

        Behandlingsinformasjon.OppholdIUtlandet(
            status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalVæreMerEnn90DagerIUtlandet,
            begrunnelse = "neh"
        ).erFerdigbehandlet() shouldBe true
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

    @Test
    fun `avslagsgrunn er utenlandsopphold dersom status er ikke skal være i utlandet`() {
        Behandlingsinformasjon.OppholdIUtlandet(
            status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalVæreMerEnn90DagerIUtlandet,
            begrunnelse = "neh"
        ).avslagsgrunn() shouldBe Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER
    }

    @Test
    fun `avslagsgrunn er null dersom status er skal holde seg i norge`() {
        Behandlingsinformasjon.OppholdIUtlandet(
            status = Behandlingsinformasjon.OppholdIUtlandet.Status.SkalHoldeSegINorge,
            begrunnelse = "neh"
        ).avslagsgrunn() shouldBe null
    }
}
