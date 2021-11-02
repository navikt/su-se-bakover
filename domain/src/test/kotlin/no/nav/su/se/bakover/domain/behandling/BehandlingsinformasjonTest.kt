package no.nav.su.se.bakover.domain.behandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.behandling.BehandlingsinformasjonTestData.behandlingsinformasjonMedAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.periode2021
import org.junit.jupiter.api.Test
import java.util.UUID
import no.nav.su.se.bakover.domain.behandling.BehandlingsinformasjonTestData as TestData

internal class BehandlingsinformasjonTest {

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

    @Test
    fun `nullstiller formue for eps dersom bosuituasjon oppdateres til ufullstendig uten eps`() {
        behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            formue = TestData.Formue.OppfyltMedEPS,
            bosituasjon = TestData.Bosituasjon.OppfyltEPSIkkeUførFlyktning,
            ektefelle = TestData.EktefellePartnerSamboer.OppyltEPSUnder67,
        ).oppdaterBosituasjonOgEktefelleOgNullstillFormueForEpsHvisIngenEps(
            bosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode2021,
            ),
            hentPerson = { KunneIkkeHentePerson.FantIkkePerson.left() },
        ) shouldBe behandlingsinformasjonMedAlleVilkårOppfylt.copy(
            formue = TestData.Formue.OppfyltUtenEPS,
            bosituasjon = TestData.Bosituasjon.OppfyltIngenEPS,
            ektefelle = TestData.EktefellePartnerSamboer.OppfyltIngenEPS,
        ).right()
    }
}
