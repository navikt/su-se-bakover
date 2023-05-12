package no.nav.su.se.bakover.domain.grunnlag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.harFjernetEllerEndretEps
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.slåSammenPeriodeOgBosituasjon
import no.nav.su.se.bakover.test.bosituasjonEpsOver67
import no.nav.su.se.bakover.test.bosituasjonEpsUnder67
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

internal class BosituasjonTest {

    @Test
    fun `viser om søker har ektefelle eller ikke`() {
        Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.UførFlyktning(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = Fnr.generer(),
        ).harEPS() shouldBe true

        Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = Fnr.generer(),
        ).harEPS() shouldBe true

        Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.SektiSyvEllerEldre(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
            fnr = Fnr.generer(),
        ).harEPS() shouldBe true

        Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
        ).harEPS() shouldBe false

        Grunnlag.Bosituasjon.Fullstendig.DelerBoligMedVoksneBarnEllerAnnenVoksen(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
        ).harEPS() shouldBe false

        Grunnlag.Bosituasjon.Ufullstendig.HarIkkeEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
        ).harEPS() shouldBe false

        Grunnlag.Bosituasjon.Ufullstendig.HarEps(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            fnr = Fnr.generer(),
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.juni(2021)),
        ).harEPS() shouldBe true
    }

    @Test
    fun `oppdaterer periode i bosituasjon`() {
        val oppdatertPeriode = Periode.create(1.februar(2021), 31.januar(2022))
        val gjeldendeBosituasjon = Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
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
            periode = januar(2021),
        )
        val b2 = b1.copy(
            periode = februar(2021),
        )
        val b3 = Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = mars(2021),
            fnr = Fnr.generer(),
        )

        val actual = listOf(b1, b2, b3).slåSammenPeriodeOgBosituasjon()
        actual.size shouldBe 2
        actual.first() shouldBe Grunnlag.Bosituasjon.Fullstendig.Enslig(
            id = b1.id,
            opprettet = fixedTidspunkt,
            periode = Periode.create(1.januar(2021), 28.februar(2021)),
        )
        actual.last() shouldBe Grunnlag.Bosituasjon.Fullstendig.EktefellePartnerSamboer.Under67.IkkeUførFlyktning(
            id = b3.id,
            opprettet = fixedTidspunkt,
            periode = mars(2021),
            fnr = b3.fnr,
        )
    }

    @Nested
    // Alle bosituasjoner med eps
    inner class HarFjernetEllerEndretEps {
        @Test
        fun `tom bosituasjon som blir oppdatert til tom - gir false`() {
            listOf<Grunnlag.Bosituasjon>().harFjernetEllerEndretEps(emptyList()) shouldBe false
        }

        @Test
        fun `fra 1 til tom - true`() {
            listOf(bosituasjonEpsOver67()).harFjernetEllerEndretEps(emptyList()) shouldBe true
        }

        @Test
        fun `fra tom til 1 - false`() {
            emptyList<Grunnlag.Bosituasjon>().harFjernetEllerEndretEps(listOf(bosituasjonEpsOver67())) shouldBe false
        }

        @Test
        fun `liste med 2, fjerner 1 eps - true`() {
            val over67 = bosituasjonEpsOver67()
            listOf(over67, bosituasjonEpsUnder67()).harFjernetEllerEndretEps(listOf(over67)) shouldBe true
        }

        @Test
        fun `liste med 2, endrer bare eps fnr - true`() {
            val over67 = bosituasjonEpsOver67()
            listOf(over67, bosituasjonEpsUnder67(fnr = Fnr.generer())).harFjernetEllerEndretEps(
                listOf(over67, bosituasjonEpsUnder67(fnr = Fnr.generer())),
            ) shouldBe true
        }

        @Test
        fun `liste med 2, endrer ingenting - false`() {
            val over67 = bosituasjonEpsOver67()
            val under67 = bosituasjonEpsUnder67()
            listOf(over67, under67).harFjernetEllerEndretEps(listOf(over67, under67)) shouldBe false
        }

        @Test
        fun `liste med 2, endrer rekkefølge - false`() {
            val over67 = bosituasjonEpsOver67()
            val under67 = bosituasjonEpsUnder67()
            listOf(over67, under67).harFjernetEllerEndretEps(listOf(under67, over67)) shouldBe false
        }

        @Test
        fun `liste med 2, legger til 1 - false`() {
            val over67 = bosituasjonEpsOver67()
            val under67 = bosituasjonEpsUnder67()
            listOf(over67, under67).harFjernetEllerEndretEps(listOf(over67, under67, bosituasjonEpsUnder67(fnr = Fnr.generer()))) shouldBe false
        }
    }
}
