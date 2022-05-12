package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

internal class FlyktningTest {

    @Test
    fun `er ikke ferdigbehandlet hvis status er uavklart`() {
        Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.Uavklart,
        ).let {
            it.erVilkårOppfylt() shouldBe false
            it.erVilkårIkkeOppfylt() shouldBe false
        }
    }

    @Test
    fun `er oppfylt hvis status er oppfylt`() {
        Behandlingsinformasjon.Flyktning(
            status = Behandlingsinformasjon.Flyktning.Status.VilkårOppfylt,
        ).erVilkårOppfylt() shouldBe true
    }
}
