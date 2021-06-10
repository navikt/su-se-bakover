package no.nav.su.se.bakover.domain.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.FnrGenerator
import org.junit.jupiter.api.Test
import java.util.UUID

internal class BosituasjonTest {

    @Test
    fun `viser om søker har ektefelle eller ikke`() {
        Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = FnrGenerator.random(),
            begrunnelse = null
        ).harEktefelle() shouldBe true

        Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = FnrGenerator.random(),
            begrunnelse = null
        ).harEktefelle() shouldBe true

        Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = FnrGenerator.random(),
            begrunnelse = null
        ).harEktefelle() shouldBe true

        Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            begrunnelse = null
        ).harEktefelle() shouldBe false

        Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            begrunnelse = null
        ).harEktefelle() shouldBe false

        Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021))
        ).harEktefelle() shouldBe false

        Grunnlag.Bosituasjon.Ufullstendig.HarEps(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            fnr = FnrGenerator.random(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021))
        ).harEktefelle() shouldBe true
    }

    @Test
    fun `returnerer false hvis 2 grunnlag har samme informasjon om ektefelle`() {
        val gjeldendeBosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarEps(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = FnrGenerator.random()
        )

        gjeldendeBosituasjon.harEndretEllerFjernetEktefelle(gjeldendeBosituasjon) shouldBe false
    }

    @Test
    fun `returnerer true hvis grunnlag om ektefelle har endret sig`() {
        val gjeldendeBosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarEps(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = FnrGenerator.random()
        )

        Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021))
        ).harEndretEllerFjernetEktefelle(gjeldendeBosituasjon) shouldBe true
    }
}
