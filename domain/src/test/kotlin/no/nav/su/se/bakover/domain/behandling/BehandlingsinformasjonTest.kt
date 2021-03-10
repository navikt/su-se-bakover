package no.nav.su.se.bakover.domain.behandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.behandling.BehandlingsinformasjonTestData.behandlingsinformasjonMedAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Sats
import org.junit.jupiter.api.Test
import no.nav.su.se.bakover.domain.behandling.BehandlingsinformasjonTestData as TestData

internal class BehandlingsinformasjonTest {

    @Test
    fun `alle vilkår må være innvilget for at summen av vilkår skal være innvilget`() {
        behandlingsinformasjonMedAlleVilkårOppfylt.erInnvilget() shouldBe true
        behandlingsinformasjonMedAlleVilkårOppfylt.erAvslag() shouldBe false
    }

    @Test
    fun `et vilkår som ikke er oppfylt fører til at summen er avslått`() {
        val info = behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            lovligOpphold = TestData.LovligOpphold.IkkeOppfylt
        )
        info.erInnvilget() shouldBe false
        info.erAvslag() shouldBe true
    }

    @Test
    fun `lister ut alle avslagsgrunner for vilkår som ikke er oppfylt`() {
        val info = behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            uførhet = TestData.Uførhet.IkkeOppfylt,
            lovligOpphold = TestData.LovligOpphold.IkkeOppfylt
        )
        info.utledAvslagsgrunner() shouldBe listOf(Avslagsgrunn.UFØRHET, Avslagsgrunn.OPPHOLDSTILLATELSE)
    }

    @Test
    fun `ingen avslagsgrunn dersom alle vilkår er oppfylt`() {
        behandlingsinformasjonMedAlleVilkårOppfylt.utledAvslagsgrunner() shouldBe emptyList()
    }

    @Test
    fun `dersom uførhet er vurdert men ikke oppfylt skal man få avslag`() {
        val info = behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            uførhet = TestData.Uførhet.IkkeOppfylt
        )
        info.erInnvilget() shouldBe false
        info.erAvslag() shouldBe true
        info.utledAvslagsgrunner() shouldBe listOf(Avslagsgrunn.UFØRHET)
    }

    @Test
    fun `dersom flyktning er vurdert men ikke oppfylt skal man få avslag`() {
        val info = behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            flyktning = TestData.Flyktning.IkkeOppfylt
        )
        info.erInnvilget() shouldBe false
        info.erAvslag() shouldBe true
        info.utledAvslagsgrunner() shouldBe listOf(Avslagsgrunn.FLYKTNING)
    }

    @Test
    fun `dersom man mangler vurdering av et vilkår er det ikke innvilget, men ikke nødvendigvis avslag`() {
        val info = behandlingsinformasjonMedAlleVilkårOppfylt.copy(
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
            institusjonsopphold = null,
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
            institusjonsopphold = null,
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
            institusjonsopphold = TestData.Institusjonsopphold.Uavklart,
            oppholdIUtlandet = TestData.OppholdIUtlandet.Uavklart,
            formue = TestData.Formue.Uavklart,
            personligOppmøte = TestData.PersonligOppmøte.Uavklart,
            bosituasjon = TestData.Bosituasjon.OppfyltDelerIkkeBolig,
            ektefelle = TestData.EktefellePartnerSamboer.OppfyltIngenEPS,
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
            institusjonsopphold = TestData.Institusjonsopphold.Oppfylt,
            oppholdIUtlandet = TestData.OppholdIUtlandet.IkkeOppfylt,
            formue = TestData.Formue.OppfyltMedEPS,
            personligOppmøte = TestData.PersonligOppmøte.Uavklart,
            bosituasjon = TestData.Bosituasjon.OppfyltDelerIkkeBolig,
            ektefelle = TestData.EktefellePartnerSamboer.OppfyltIngenEPS,
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
            institusjonsopphold = TestData.Institusjonsopphold.Oppfylt,
            oppholdIUtlandet = TestData.OppholdIUtlandet.Uavklart,
            formue = TestData.Formue.OppfyltMedEPS,
            personligOppmøte = TestData.PersonligOppmøte.Uavklart,
            bosituasjon = TestData.Bosituasjon.OppfyltDelerIkkeBolig,
            ektefelle = TestData.EktefellePartnerSamboer.OppfyltIngenEPS,
        )

        uferdig.erInnvilget() shouldBe false
        uferdig.erAvslag() shouldBe false
    }

    @Test
    fun `bosituasjon er null skal returnere BosituasjonErUbesvart`() {
        val ferdig = behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            formue = TestData.Formue.OppfyltMedEPS,
            bosituasjon = null,
            ektefelle = TestData.EktefellePartnerSamboer.OppfyltIngenEPS,
        )
        ferdig.getBeregningStrategy() shouldBe Behandlingsinformasjon.UfullstendigBehandlingsinformasjon.BosituasjonErUbesvart.left()
        ferdig.getSatsgrunn() shouldBe Behandlingsinformasjon.UfullstendigBehandlingsinformasjon.BosituasjonErUbesvart.left()
        ferdig.getBeregningStrategy() shouldBe Behandlingsinformasjon.UfullstendigBehandlingsinformasjon.BosituasjonErUbesvart.left()
    }

    @Test
    fun `ektefelle er null skal returnere EktefelleErUbesvart`() {
        val ferdig = behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            formue = TestData.Formue.OppfyltMedEPS,
            bosituasjon = TestData.Bosituasjon.OppfyltEPSUførFlyktning,
            ektefelle = null,
        )
        ferdig.getBeregningStrategy() shouldBe Behandlingsinformasjon.UfullstendigBehandlingsinformasjon.EktefelleErUbesvart.left()
        ferdig.getSatsgrunn() shouldBe Behandlingsinformasjon.UfullstendigBehandlingsinformasjon.EktefelleErUbesvart.left()
        ferdig.utledSats() shouldBe Behandlingsinformasjon.UfullstendigBehandlingsinformasjon.EktefelleErUbesvart.left()
    }

    @Test
    fun `ektefelle og bosituasjon er null skal returnere EktefelleErUbesvart`() {
        val ferdig = behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            formue = TestData.Formue.OppfyltMedEPS,
            bosituasjon = null,
            ektefelle = null,
        )
        ferdig.getBeregningStrategy() shouldBe Behandlingsinformasjon.UfullstendigBehandlingsinformasjon.EktefelleErUbesvart.left()
        ferdig.getSatsgrunn() shouldBe Behandlingsinformasjon.UfullstendigBehandlingsinformasjon.EktefelleErUbesvart.left()
        ferdig.utledSats() shouldBe Behandlingsinformasjon.UfullstendigBehandlingsinformasjon.EktefelleErUbesvart.left()
    }

    @Test
    fun `EPS over 67 skal returnerene Eps67EllerEldre uansett om ufør flykting fyllt ut eller ikke`() {
        val uførFlyktningTrue = behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            formue = TestData.Formue.OppfyltMedEPS,
            bosituasjon = TestData.Bosituasjon.OppfyltEPSUførFlyktning,
            ektefelle = TestData.EktefellePartnerSamboer.OppyltEPSOverEllerLik67,
        )
        uførFlyktningTrue.getBeregningStrategy() shouldBe BeregningStrategy.Eps67EllerEldre.right()
        uførFlyktningTrue.getSatsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_67_ELLER_ELDRE.right()
        uførFlyktningTrue.utledSats() shouldBe Sats.ORDINÆR.right()

        val uførFlyktningFalse = behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            formue = TestData.Formue.OppfyltMedEPS,
            bosituasjon = TestData.Bosituasjon.OppfyltEPSIkkeUførFlyktning,
            ektefelle = TestData.EktefellePartnerSamboer.OppyltEPSOverEllerLik67,
        )
        uførFlyktningFalse.getBeregningStrategy() shouldBe BeregningStrategy.Eps67EllerEldre.right()
        uførFlyktningTrue.getSatsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_67_ELLER_ELDRE.right()
        uførFlyktningTrue.utledSats() shouldBe Sats.ORDINÆR.right()

        val uføreFlyktningIkkeFylltUt = behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            formue = TestData.Formue.OppfyltMedEPS,
            bosituasjon = TestData.Bosituasjon.OppfyltEPSUførFlyktningIkkeUtfyllt,
            ektefelle = TestData.EktefellePartnerSamboer.OppyltEPSOverEllerLik67,
        )
        uføreFlyktningIkkeFylltUt.getBeregningStrategy() shouldBe BeregningStrategy.Eps67EllerEldre.right()
        uføreFlyktningIkkeFylltUt.getSatsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_67_ELLER_ELDRE.right()
        uføreFlyktningIkkeFylltUt.utledSats() shouldBe Sats.ORDINÆR.right()
    }

    @Test
    fun `EPS under 67 og ufør flyktning ikke fyllt ut skal returnere EpsUførFlyktningErUbesvart`() {
        val ferdig = behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            formue = TestData.Formue.OppfyltMedEPS,
            bosituasjon = TestData.Bosituasjon.OppfyltEPSUførFlyktningIkkeUtfyllt,
            ektefelle = TestData.EktefellePartnerSamboer.OppyltEPSUnder67,
        )

        ferdig.getBeregningStrategy() shouldBe Behandlingsinformasjon.UfullstendigBehandlingsinformasjon.EpsUførFlyktningErUbesvart.left()
        ferdig.getSatsgrunn() shouldBe Behandlingsinformasjon.UfullstendigBehandlingsinformasjon.EpsUførFlyktningErUbesvart.left()
        ferdig.utledSats() shouldBe Behandlingsinformasjon.UfullstendigBehandlingsinformasjon.EpsUførFlyktningErUbesvart.left()
    }

    @Test
    fun `EPS under 67 og ufør flyktning`() {
        val ferdig = behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            formue = TestData.Formue.OppfyltMedEPS,
            bosituasjon = TestData.Bosituasjon.OppfyltEPSUførFlyktning,
            ektefelle = TestData.EktefellePartnerSamboer.OppyltEPSUnder67,
        )
        ferdig.getBeregningStrategy() shouldBe BeregningStrategy.EpsUnder67ÅrOgUførFlyktning.right()
        ferdig.getSatsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING.right()
        ferdig.utledSats() shouldBe Sats.ORDINÆR.right()
    }

    @Test
    fun `EPS under 67 og ikke ufør flyktning`() {
        val ferdig = behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            formue = TestData.Formue.OppfyltMedEPS,
            bosituasjon = TestData.Bosituasjon.OppfyltEPSIkkeUførFlyktning,
            ektefelle = TestData.EktefellePartnerSamboer.OppyltEPSUnder67,
        )
        ferdig.getBeregningStrategy() shouldBe BeregningStrategy.EpsUnder67År.right()
        ferdig.getSatsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67.right()
        ferdig.utledSats() shouldBe Sats.HØY.right()
    }

    @Test
    fun `har ikke EPS og deler bolig ikke fyllt ut gir DelerBoligErUbesvart`() {
        val ferdig = behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            formue = TestData.Formue.OppfyltUtenEPS,
            bosituasjon = TestData.Bosituasjon.IkkeOppfylltDelerBoligIkkeUtfyllt,
            ektefelle = TestData.EktefellePartnerSamboer.OppfyltIngenEPS,
        )
        ferdig.getBeregningStrategy() shouldBe Behandlingsinformasjon.UfullstendigBehandlingsinformasjon.DelerBoligErUbesvart.left()
        ferdig.utledSats() shouldBe Behandlingsinformasjon.UfullstendigBehandlingsinformasjon.DelerBoligErUbesvart.left()
        ferdig.getSatsgrunn() shouldBe Behandlingsinformasjon.UfullstendigBehandlingsinformasjon.DelerBoligErUbesvart.left()
    }

    @Test
    fun `har ikke EPS og deler bolig`() {
        val ferdig = behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            formue = TestData.Formue.OppfyltUtenEPS,
            bosituasjon = TestData.Bosituasjon.OppfyltDelerBolig,
            ektefelle = TestData.EktefellePartnerSamboer.OppfyltIngenEPS,
        )
        ferdig.getBeregningStrategy() shouldBe BeregningStrategy.BorMedVoksne.right()
        ferdig.getSatsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN.right()
        ferdig.utledSats() shouldBe Sats.ORDINÆR.right()
    }

    @Test
    fun `har ikke EPS og deler ikke bolig`() {
        val ferdig = behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            formue = TestData.Formue.OppfyltUtenEPS,
            bosituasjon = TestData.Bosituasjon.OppfyltDelerIkkeBolig,
            ektefelle = TestData.EktefellePartnerSamboer.OppfyltIngenEPS,
        )
        ferdig.getBeregningStrategy() shouldBe BeregningStrategy.BorAlene.right()
        ferdig.getSatsgrunn() shouldBe Satsgrunn.ENSLIG.right()
        ferdig.utledSats() shouldBe Sats.HØY.right()
    }
}
