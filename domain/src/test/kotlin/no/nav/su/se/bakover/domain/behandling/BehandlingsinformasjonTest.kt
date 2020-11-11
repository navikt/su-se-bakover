package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.brev.Avslagsgrunn
import org.junit.jupiter.api.Test
import no.nav.su.se.bakover.domain.behandling.BehandlingsinformasjonTestData as TestData

internal class BehandlingsinformasjonTest {

    @Test
    fun `alle vilkår må være innvilget for at summen av vilkår skal være innvilget`() {
        TestData.behandlingsinformasjonMedAlleVilkårOppfylt.erInnvilget() shouldBe true
        TestData.behandlingsinformasjonMedAlleVilkårOppfylt.erAvslag() shouldBe false
    }

    @Test
    fun `et vilkår som ikke er oppfylt fører til at summen er avslått`() {
        val info = TestData.behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            lovligOpphold = TestData.LovligOpphold.IkkeOppfylt
        )
        info.erInnvilget() shouldBe false
        info.erAvslag() shouldBe true
    }

    @Test
    fun `inkluderer bare den første avslagsgrunnen for vilkår som ikke er oppfylt`() {
        val info = TestData.behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            uførhet = TestData.Uførhet.IkkeOppfylt,
            lovligOpphold = TestData.LovligOpphold.IkkeOppfylt
        )
        info.getAvslagsgrunn() shouldBe Avslagsgrunn.UFØRHET
    }

    @Test
    fun `ingen avslagsgrunn dersom alle vilkår er oppfylt`() {
        TestData.behandlingsinformasjonMedAlleVilkårOppfylt.getAvslagsgrunn() shouldBe null
    }

    @Test
    fun `dersom uførhet er vurdert men ikke oppfylt skal man få avslag`() {
        val info = TestData.behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            uførhet = TestData.Uførhet.IkkeOppfylt
        )
        info.erInnvilget() shouldBe false
        info.erAvslag() shouldBe true
        info.getAvslagsgrunn() shouldBe Avslagsgrunn.UFØRHET
    }

    @Test
    fun `dersom flyktning er vurdert men ikke oppfylt skal man få avslag`() {
        val info = TestData.behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            flyktning = TestData.Flyktning.IkkeOppfylt
        )
        info.erInnvilget() shouldBe false
        info.erAvslag() shouldBe true
        info.getAvslagsgrunn() shouldBe Avslagsgrunn.FLYKTNING
    }

    @Test
    fun `dersom man mangler vurdering av et vilkår er det ikke innvilget, men ikke nødvendigvis avslag`() {
        val info = TestData.behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            lovligOpphold = null
        )
        info.erInnvilget() shouldBe false
        info.erAvslag() shouldBe false
        info.getAvslagsgrunn() shouldBe null
    }

    @Test
    fun `dersom uførhet og flyktning er fylt ut, men en av dem gir avslag, skal man få avslag uten å fylle inn resten`() {
        val ikkeOppfyltUfør = Behandlingsinformasjon(
            uførhet = TestData.Uførhet.IkkeOppfylt,
            flyktning = TestData.Flyktning.Oppfylt,
            lovligOpphold = null,
            fastOppholdINorge = null,
            oppholdIUtlandet = null,
            formue = null,
            personligOppmøte = null,
            bosituasjon = null,
            ektefelle = null,
        )
        ikkeOppfyltUfør.erInnvilget() shouldBe false
        ikkeOppfyltUfør.erAvslag() shouldBe true

        val ikkeOppfyltFlyktning = Behandlingsinformasjon(
            uførhet = TestData.Uførhet.Oppfylt,
            flyktning = TestData.Flyktning.IkkeOppfylt,
            lovligOpphold = null,
            fastOppholdINorge = null,
            oppholdIUtlandet = null,
            formue = null,
            personligOppmøte = null,
            bosituasjon = null,
            ektefelle = null,
        )
        ikkeOppfyltFlyktning.erInnvilget() shouldBe false
        ikkeOppfyltFlyktning.erAvslag() shouldBe true
    }
}
