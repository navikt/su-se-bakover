package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.brev.Avslagsgrunn
import org.junit.jupiter.api.Test

internal class FlyktningTest {
    @Test
    fun `er gyldig uansett hva man putter inn`() {
        Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
            begrunnelse = "neh"
        ).erGyldig() shouldBe true

        Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårIkkeOppfylt,
            begrunnelse = null
        ).erGyldig() shouldBe true

        Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.Uavklart,
            begrunnelse = "neh"
        ).erGyldig() shouldBe true
    }

    @Test
    fun `er ferdigbehandlet hvis status er oppfylt`() {
        Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
            begrunnelse = "neh"
        ).erFerdigbehandlet() shouldBe true
    }

    @Test
    fun `er ferdigbehandlet hvis status er ikke oppfylt`() {
        Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårIkkeOppfylt,
            begrunnelse = "neh"
        ).erFerdigbehandlet() shouldBe true
    }

    @Test
    fun `er ikke ferdigbehandlet hvis status er ikke uavklart`() {
        Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.Uavklart,
            begrunnelse = "neh"
        ).erFerdigbehandlet() shouldBe false
    }

    @Test
    fun `er oppfylt hvis status er oppfylt`() {
        Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
            begrunnelse = "neh"
        ).erVilkårOppfylt() shouldBe true
    }

    @Test
    fun `avslagsgrunn er flyktning dersom status er ikke oppfylt`() {
        Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårIkkeOppfylt,
            begrunnelse = "neh"
        ).avslagsgrunn() shouldBe Avslagsgrunn.FLYKTNING
    }

    @Test
    fun `avslagsgrunn er null dersom status er oppfylt`() {
        Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
            begrunnelse = "neh"
        ).avslagsgrunn() shouldBe null
    }
}
