package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class BosituasjonTest {

    @Test
    fun `vilkår er oppfyllt når delerBolig eller EPS-uførFlyktning er ikke null`() {
        val info = Behandlingsinformasjon.Bosituasjon(
            delerBolig = false,
            ektemakeEllerSamboerUførFlyktning = null,
            begrunnelse = null
        )
        info.erVilkårOppfylt() shouldBe true
    }

    @Test
    fun `vilkår er ikke oppfyllt når delerBolig og EPS-uførFlyktning er ikke null`() {
        val info = Behandlingsinformasjon.Bosituasjon(
            delerBolig = false,
            ektemakeEllerSamboerUførFlyktning = false,
            begrunnelse = null
        )
        assertThrows<IllegalStateException> {
            info.erVilkårOppfylt()
        }
    }

    @Test
    fun `vilkår er ikke oppfyllt når delerBolig og EPS-uførFlyktning er null`() {
        val info = Behandlingsinformasjon.Bosituasjon(
            delerBolig = null,
            ektemakeEllerSamboerUførFlyktning = null,
            begrunnelse = null
        )
        info.erVilkårOppfylt() shouldBe false
    }
}
