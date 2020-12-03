package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.beregning.BeregningStrategy
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import org.junit.jupiter.api.Test

internal class BosituasjonTest {

    @Test
    fun `deler ikke bolig`() {
        val info = Behandlingsinformasjon.Bosituasjon(
            epsFnr = null,
            delerBolig = false,
            ektemakeEllerSamboerUførFlyktning = null,
            begrunnelse = null
        )
        info.getSatsgrunn() shouldBe Satsgrunn.ENSLIG
        info.getBeregningStrategy() shouldBe BeregningStrategy.BorAlene
        info.getBeregningStrategy().sats() shouldBe Sats.HØY
        info.getBeregningStrategy().fradragStrategy() shouldBe FradragStrategy.Enslig
    }

    @Test
    fun `deler bolig med voksne barn`() {
        val info = Behandlingsinformasjon.Bosituasjon(
            epsFnr = null,
            delerBolig = true,
            ektemakeEllerSamboerUførFlyktning = null,
            begrunnelse = null
        )
        info.getSatsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
        info.getBeregningStrategy() shouldBe BeregningStrategy.BorMedVoksne
        info.getBeregningStrategy().sats() shouldBe Sats.ORDINÆR
        info.getBeregningStrategy().fradragStrategy() shouldBe FradragStrategy.Enslig
    }

    @Test
    fun `deler bolig med annen voksen`() {
        val info = Behandlingsinformasjon.Bosituasjon(
            epsFnr = null,
            delerBolig = true,
            ektemakeEllerSamboerUførFlyktning = null,
            begrunnelse = null
        )
        info.getSatsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
        info.getBeregningStrategy() shouldBe BeregningStrategy.BorMedVoksne
        info.getBeregningStrategy().sats() shouldBe Sats.ORDINÆR
        info.getBeregningStrategy().fradragStrategy() shouldBe FradragStrategy.Enslig
    }

    @Test
    fun `deler bolig med ektemake samboer og ektemake er over 67 år`() {

        val info = Behandlingsinformasjon.Bosituasjon(
            epsFnr = Fnr("01010012345"),
            delerBolig = null,
            ektemakeEllerSamboerUførFlyktning = null,
            begrunnelse = null
        )
        info.getSatsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_OVER_67
        info.getBeregningStrategy() shouldBe BeregningStrategy.EpsOver67År
        info.getBeregningStrategy().sats() shouldBe Sats.ORDINÆR
        info.getBeregningStrategy().fradragStrategy() shouldBe FradragStrategy.EpsOver67År
    }

    @Test
    fun `deler bolig med ektemake samboer og ektemake er under 67 år og ikke ufør flyktning`() {
        val epsFnr = Fnr("01019012345")

        val info = Behandlingsinformasjon.Bosituasjon(
            epsFnr = epsFnr,
            delerBolig = null,
            ektemakeEllerSamboerUførFlyktning = false,
            begrunnelse = null
        )
        info.getSatsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67
        info.getBeregningStrategy() shouldBe BeregningStrategy.EpsUnder67År
        info.getBeregningStrategy().sats() shouldBe Sats.HØY
        info.getBeregningStrategy().fradragStrategy() shouldBe FradragStrategy.EpsUnder67År
    }

    @Test
    fun `deler bolig med ektemake samboer og ektemake er under 67 år og ufør flyktning`() {
        val info = Behandlingsinformasjon.Bosituasjon(
            epsFnr = Fnr("05019445102"),
            delerBolig = null,
            ektemakeEllerSamboerUførFlyktning = true,
            begrunnelse = null
        )
        info.getSatsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
        info.getBeregningStrategy() shouldBe BeregningStrategy.EpsUnder67ÅrOgUførFlyktning
        info.getBeregningStrategy().sats() shouldBe Sats.ORDINÆR
        info.getBeregningStrategy().fradragStrategy() shouldBe FradragStrategy.EpsUnder67ÅrOgUførFlyktning
    }
}
