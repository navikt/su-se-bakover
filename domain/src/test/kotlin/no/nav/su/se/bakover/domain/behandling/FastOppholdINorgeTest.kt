package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import org.junit.jupiter.api.Test

internal class FastOppholdINorgeTest {
    @Test
    fun `er gyldig uansett hva man putter inn`() {
        Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
            begrunnelse = "neh"
        ).erGyldig() shouldBe true

        Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårIkkeOppfylt,
            begrunnelse = null
        ).erGyldig() shouldBe true

        Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.Uavklart,
            begrunnelse = "neh"
        ).erGyldig() shouldBe true
    }

    @Test
    fun `er ikke ferdigbehandlet hvis status er uavklart`() {
        Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.Uavklart,
            begrunnelse = "neh"
        ).let {
            it.erVilkårOppfylt() shouldBe false
            it.erVilkårIkkeOppfylt() shouldBe false
        }
    }

    @Test
    fun `er oppfylt hvis status er oppfylt`() {
        Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
            begrunnelse = "neh"
        ).erVilkårOppfylt() shouldBe true
    }

    @Test
    fun `er ikke oppfylt hvis status er ikke oppfylt`() {
        Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårIkkeOppfylt,
            begrunnelse = "neh"
        ).erVilkårOppfylt() shouldBe false
    }

    @Test
    fun `avslagsgrunn er oppholdstillatelse dersom status er ikke oppfylt`() {
        Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårIkkeOppfylt,
            begrunnelse = "neh"
        ).avslagsgrunn() shouldBe Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE
    }

    @Test
    fun `avslagsgrunn er null dersom status er oppfylt`() {
        Behandlingsinformasjon.FastOppholdINorge(
            status = Behandlingsinformasjon.FastOppholdINorge.Status.VilkårOppfylt,
            begrunnelse = "neh"
        ).avslagsgrunn() shouldBe null
    }
}
