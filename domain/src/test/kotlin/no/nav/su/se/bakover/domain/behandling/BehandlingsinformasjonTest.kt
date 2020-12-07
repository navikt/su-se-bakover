package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.behandling.Behandling.Companion.utledAvslagsgrunner
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Beregningsgrunnlag
import no.nav.su.se.bakover.domain.beregning.Sats
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
    fun `lister ut alle avslagsgrunner for vilkår som ikke er oppfylt`() {
        val info = TestData.behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            uførhet = TestData.Uførhet.IkkeOppfylt,
            lovligOpphold = TestData.LovligOpphold.IkkeOppfylt
        )
        info.utledAvslagsgrunner() shouldBe listOf(Avslagsgrunn.UFØRHET, Avslagsgrunn.OPPHOLDSTILLATELSE)
    }

    @Test
    fun `ingen avslagsgrunn dersom alle vilkår er oppfylt`() {
        TestData.behandlingsinformasjonMedAlleVilkårOppfylt.utledAvslagsgrunner() shouldBe emptyList()
    }

    @Test
    fun `dersom uførhet er vurdert men ikke oppfylt skal man få avslag`() {
        val info = TestData.behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            uførhet = TestData.Uførhet.IkkeOppfylt
        )
        info.erInnvilget() shouldBe false
        info.erAvslag() shouldBe true
        info.utledAvslagsgrunner() shouldBe listOf(Avslagsgrunn.UFØRHET)
    }

    @Test
    fun `dersom flyktning er vurdert men ikke oppfylt skal man få avslag`() {
        val info = TestData.behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            flyktning = TestData.Flyktning.IkkeOppfylt
        )
        info.erInnvilget() shouldBe false
        info.erAvslag() shouldBe true
        info.utledAvslagsgrunner() shouldBe listOf(Avslagsgrunn.FLYKTNING)
    }

    @Test
    fun `dersom man mangler vurdering av et vilkår er det ikke innvilget, men ikke nødvendigvis avslag`() {
        val info = TestData.behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            lovligOpphold = null
        )
        info.erInnvilget() shouldBe false
        info.erAvslag() shouldBe false
        info.utledAvslagsgrunner() shouldBe emptyList()
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

    @Test
    fun `dersom ett vilkår er ikke-oppfylt og alle de andre er uavklart, skal det gi avslag`() {
        val ikkeOppfyltFastOpphold = Behandlingsinformasjon(
            uførhet = TestData.Uførhet.Uavklart,
            flyktning = TestData.Flyktning.Uavklart,
            lovligOpphold = TestData.LovligOpphold.Uavklart,
            fastOppholdINorge = TestData.FastOppholdINorge.IkkeOppfylt,
            oppholdIUtlandet = TestData.OppholdIUtlandet.Uavklart,
            formue = TestData.Formue.Uavklart,
            personligOppmøte = TestData.PersonligOppmøte.Uavklart,
            bosituasjon = TestData.Bosituasjon.Oppfylt,
            ektefelle = TestData.EktefellePartnerSamboer.Oppfylt,
        )

        ikkeOppfyltFastOpphold.erInnvilget() shouldBe false
        ikkeOppfyltFastOpphold.erAvslag() shouldBe true
    }

    @Test
    fun `dersom ett vilkår er ikke-oppfylt og alle de andre er enten uavklart eller oppfylt, skal det gi avslag`() {
        val ikkeOppfyltOppholdIUtlandet = Behandlingsinformasjon(
            uførhet = TestData.Uførhet.Oppfylt,
            flyktning = TestData.Flyktning.Oppfylt,
            lovligOpphold = TestData.LovligOpphold.Uavklart,
            fastOppholdINorge = TestData.FastOppholdINorge.Uavklart,
            oppholdIUtlandet = TestData.OppholdIUtlandet.IkkeOppfylt,
            formue = TestData.Formue.Oppfylt,
            personligOppmøte = TestData.PersonligOppmøte.Uavklart,
            bosituasjon = TestData.Bosituasjon.Oppfylt,
            ektefelle = TestData.EktefellePartnerSamboer.Oppfylt,
        )

        ikkeOppfyltOppholdIUtlandet.erInnvilget() shouldBe false
        ikkeOppfyltOppholdIUtlandet.erAvslag() shouldBe true
    }

    @Test
    fun `dersom ingen vilkår er ikke-oppfylt og ett eller flere er uavklart, så er ikke behandlingen ferdig`() {
        val uferdig = Behandlingsinformasjon(
            uførhet = TestData.Uførhet.Oppfylt,
            flyktning = TestData.Flyktning.Oppfylt,
            lovligOpphold = TestData.LovligOpphold.Uavklart,
            fastOppholdINorge = TestData.FastOppholdINorge.Uavklart,
            oppholdIUtlandet = TestData.OppholdIUtlandet.Uavklart,
            formue = TestData.Formue.Oppfylt,
            personligOppmøte = TestData.PersonligOppmøte.Uavklart,
            bosituasjon = TestData.Bosituasjon.Oppfylt,
            ektefelle = TestData.EktefellePartnerSamboer.Oppfylt,
        )

        uferdig.erInnvilget() shouldBe false
        uferdig.erAvslag() shouldBe false
    }

    @Test
    fun `beregningen skal kunne gi en eller ingen avslagsgrunn`() {
        val periode = Periode(1.januar(2020), 31.januar(2020))

        val underMinstegrense = BeregningStrategy.BorAlene.beregn(
            Beregningsgrunnlag(
                beregningsperiode = periode,
                fraSaksbehandler = emptyList(),
                forventetInntekt = (Sats.HØY.månedsbeløp(1.januar(2020)) - 200) * 12
            )
        )
        underMinstegrense.utledAvslagsgrunner() shouldBe listOf(Avslagsgrunn.SU_UNDER_MINSTEGRENSE)

        val forHøyInntekt = BeregningStrategy.BorAlene.beregn(
            Beregningsgrunnlag(
                beregningsperiode = periode,
                fraSaksbehandler = emptyList(),
                forventetInntekt = (Sats.HØY.månedsbeløp(1.januar(2020)) * 4) * 12
            )
        )

        forHøyInntekt.utledAvslagsgrunner() shouldBe listOf(Avslagsgrunn.FOR_HØY_INNTEKT)

        val ingen = BeregningStrategy.BorAlene.beregn(
            Beregningsgrunnlag(
                beregningsperiode = periode,
                fraSaksbehandler = emptyList(),
                forventetInntekt = (Sats.HØY.månedsbeløp(1.januar(2020)) - 5000) * 12
            )
        )

        ingen.utledAvslagsgrunner() shouldBe emptyList()
    }
}
