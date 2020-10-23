package no.nav.su.se.bakover.domain.behandling

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Boforhold
import no.nav.su.se.bakover.domain.brev.Satsgrunn
import org.junit.jupiter.api.Test

internal class BosituasjonTest {
    @Test
    fun `deler ikke bolig`() {
        Behandlingsinformasjon.Bosituasjon(
            delerBolig = false,
            delerBoligMed = null,
            ektemakeEllerSamboerUnder67År = null,
            ektemakeEllerSamboerUførFlyktning = null,
            begrunnelse = null
        ).getSatsgrunn() shouldBe Satsgrunn.ENSLIG
    }

    @Test
    fun `deler bolig med voksne barn`() {
        Behandlingsinformasjon.Bosituasjon(
            delerBolig = true,
            delerBoligMed = Boforhold.DelerBoligMed.VOKSNE_BARN,
            ektemakeEllerSamboerUnder67År = null,
            ektemakeEllerSamboerUførFlyktning = null,
            begrunnelse = null
        ).getSatsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
    }

    @Test
    fun `deler bolig med annen voksen`() {
        Behandlingsinformasjon.Bosituasjon(
            delerBolig = true,
            delerBoligMed = Boforhold.DelerBoligMed.ANNEN_VOKSEN,
            ektemakeEllerSamboerUnder67År = null,
            ektemakeEllerSamboerUførFlyktning = null,
            begrunnelse = null
        ).getSatsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_VOKSNE_BARN_ELLER_ANNEN_VOKSEN
    }

    @Test
    fun `deler bolig med ektemake samboer og ektemake er over 67 år`() {
        Behandlingsinformasjon.Bosituasjon(
            delerBolig = true,
            delerBoligMed = Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER,
            ektemakeEllerSamboerUnder67År = false,
            ektemakeEllerSamboerUførFlyktning = null,
            begrunnelse = null
        ).getSatsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_OVER_67
    }

    @Test
    fun `deler bolig med ektemake samboer og ektemake er under 67 år og ikke ufør flyktning`() {
        Behandlingsinformasjon.Bosituasjon(
            delerBolig = true,
            delerBoligMed = Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER,
            ektemakeEllerSamboerUnder67År = true,
            ektemakeEllerSamboerUførFlyktning = false,
            begrunnelse = null
        ).getSatsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67
    }

    @Test
    fun `deler bolig med ektemake samboer og ektemake er under 67 år og ufør flyktning`() {
        Behandlingsinformasjon.Bosituasjon(
            delerBolig = true,
            delerBoligMed = Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER,
            ektemakeEllerSamboerUnder67År = true,
            ektemakeEllerSamboerUførFlyktning = true,
            begrunnelse = null
        ).getSatsgrunn() shouldBe Satsgrunn.DELER_BOLIG_MED_EKTEMAKE_SAMBOER_UNDER_67_UFØR_FLYKTNING
    }
}
