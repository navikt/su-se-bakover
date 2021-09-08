package no.nav.su.se.bakover.domain.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import java.util.UUID

internal class BosituasjonTest {

    @Test
    fun `viser om søker har ektefelle eller ikke`() {
        Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = Fnr.generer(),
            begrunnelse = null,
        ).harEktefelle() shouldBe true

        Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = Fnr.generer(),
            begrunnelse = null,
        ).harEktefelle() shouldBe true

        Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = Fnr.generer(),
            begrunnelse = null,
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
            fnr = Fnr.generer(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
        ).harEktefelle() shouldBe true
    }

    @Test
    fun `returnerer false hvis 2 grunnlag har samme informasjon om ektefelle`() {
        val gjeldendeBosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarEps(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = Fnr.generer(),
        )

        gjeldendeBosituasjon.harEndretEllerFjernetEktefelle(gjeldendeBosituasjon) shouldBe false
    }

    @Test
    fun `returnerer true hvis grunnlag om ektefelle har endret sig`() {
        val gjeldendeBosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarEps(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = Fnr.generer(),
        )

        Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
        ).harEndretEllerFjernetEktefelle(gjeldendeBosituasjon) shouldBe true
    }

    @Test
    fun `oppdaterer periode i bosituasjon`() {
        val oppdatertPeriode = Periode.create(1.februar(2021), 31.januar(2022))
        val gjeldendeBosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
        )

        gjeldendeBosituasjon.oppdaterBosituasjonsperiode(oppdatertPeriode) shouldBe gjeldendeBosituasjon.copy(
            periode = oppdatertPeriode,
        )
    }
}
