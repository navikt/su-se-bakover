package no.nav.su.se.bakover.domain.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.slåSammenPeriodeOgBosituasjon
import no.nav.su.se.bakover.domain.person.Fnr
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class BosituasjonTest {

    @Test
    fun `viser om søker har ektefelle eller ikke`() {
        Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = Fnr.generer(),
            begrunnelse = null,
        ).harEktefelle() shouldBe true

        Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = Fnr.generer(),
            begrunnelse = null,
        ).harEktefelle() shouldBe true

        Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = Fnr.generer(),
            begrunnelse = null,
        ).harEktefelle() shouldBe true

        Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            begrunnelse = null,
        ).harEktefelle() shouldBe false

        Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            begrunnelse = null,
        ).harEktefelle() shouldBe false

        Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
        ).harEktefelle() shouldBe false

        Grunnlag.Bosituasjon.Ufullstendig.HarEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fnr = Fnr.generer(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
        ).harEktefelle() shouldBe true
    }

    @Test
    fun `returnerer false hvis 2 grunnlag har samme informasjon om ektefelle`() {
        val gjeldendeBosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = Fnr.generer(),
        )

        gjeldendeBosituasjon.harEndretEllerFjernetEktefelle(gjeldendeBosituasjon) shouldBe false
    }

    @Test
    fun `returnerer true hvis grunnlag om ektefelle har endret sig`() {
        val gjeldendeBosituasjon = Grunnlag.Bosituasjon.Ufullstendig.HarEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = Fnr.generer(),
        )

        Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
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

    @Test
    fun `slår sammen like og tilstøtende bosituasjoner`() {
        val b1 = Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.januar(2021), 31.januar(2021)),
            begrunnelse = null,
        )
        val b2 = b1.copy(
            periode = Periode.create(1.februar(2021), 28.februar(2021)),
        )
        val b3 = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.mars(2021), 31.mars(2021)),
            fnr = Fnr.generer(),
            begrunnelse = null,
        )

        val actual = listOf(b1, b2, b3).slåSammenPeriodeOgBosituasjon()
        actual.size shouldBe 2
        actual.first() shouldBe Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = b1.id,
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.januar(2021), 28.februar(2021)),
            begrunnelse = null,
        )
        actual.last() shouldBe Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
            id = b3.id,
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.mars(2021), 31.mars(2021)),
            fnr = b3.fnr,
            begrunnelse = null,
        )
    }

    @Test
    fun `kaster exception dersom det finnes ufullstendig bostiuasjon når den skal slå sammen`() {
        val b1 = Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.januar(2021), 31.januar(2021)),
            begrunnelse = null,
        )
        val b2 = Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.februar(2021), 28.februar(2021)),
        )

        assertThrows<IllegalStateException> {
            listOf(b1, b2).slåSammenPeriodeOgBosituasjon()
        }
    }
}
