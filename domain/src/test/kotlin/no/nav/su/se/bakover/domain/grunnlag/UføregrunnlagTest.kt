package no.nav.su.se.bakover.domain.grunnlag

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.application.CopyArgs
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Uføregrunnlag.Companion.slåSammenPeriodeOgUføregrad
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import java.util.UUID

internal class UføregrunnlagTest {

    @Test
    fun `bevarer korrekte verdier ved kopiering for plassering på tidslinje - full kopi`() {
        val original = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
            uføregrad = Uføregrad.parse(50),
            forventetInntekt = 500,
        )
        original.copy(CopyArgs.Tidslinje.Full).let {
            it.id shouldNotBe original.id
            it.opprettet shouldBe original.opprettet
            it.periode shouldBe original.periode
            it.uføregrad shouldBe original.uføregrad
            it.forventetInntekt shouldBe original.forventetInntekt
        }
    }

    @Test
    fun `bevarer korrekte verdier ved kopiering for plassering på tidslinje - ny periode`() {
        val original = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = år(2021),
            uføregrad = Uføregrad.parse(50),
            forventetInntekt = 500,
        )
        original.copy(
            CopyArgs.Tidslinje.NyPeriode(
                periode = Periode.create(1.mai(2021), 31.desember(2021)),
            ),
        ).let {
            it.id shouldNotBe original.id
            it.opprettet shouldBe original.opprettet
            it.periode shouldBe Periode.create(1.mai(2021), 31.desember(2021))
            it.uføregrad shouldBe original.uføregrad
            it.forventetInntekt shouldBe original.forventetInntekt
        }
    }

    @Test
    fun `tom uføregrunnlag liste gir ut en tom liste tilbake`() {
        emptyList<Grunnlag.Uføregrunnlag>().slåSammenPeriodeOgUføregrad() shouldBe emptyList()
    }

    @Test
    fun `liste med 1 uføregrunnlag returnerer sin periode og uføregrad`() {
        val periode = år(2021)
        val uføregrad = Uføregrad.parse(20)
        val uføregrunnlag = listOf(
            Grunnlag.Uføregrunnlag(
                id = UUID.randomUUID(),
                opprettet = fixedTidspunkt,
                periode = periode,
                uføregrad = uføregrad,
                forventetInntekt = 0,
            ),
        )

        uføregrunnlag.slåSammenPeriodeOgUføregrad() shouldBe listOf(
            Pair(periode, uføregrad),
        )
    }

    @Test
    fun `slår sammen uføregrunnlag som er like og tilstøtende`() {
        val januarApril = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 30.april(2021)),
            uføregrad = Uføregrad.parse(20),
            forventetInntekt = 0,
        )
        val mai = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = mai(2021),
            uføregrad = Uføregrad.parse(20),
            forventetInntekt = 0,
        )
        val juniDesember = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = Periode.create(fraOgMed = 1.juni(2021), tilOgMed = 31.desember(2021)),
            uføregrad = Uføregrad.parse(40),
            forventetInntekt = 0,
        )
        val listeAvUføregrunnlag = listOf(januarApril, mai, juniDesember)

        listeAvUføregrunnlag.slåSammenPeriodeOgUføregrad() shouldBe listOf(
            Pair(Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.mai(2021)), Uføregrad.parse(20)),
            Pair(Periode.create(fraOgMed = 1.juni(2021), tilOgMed = 31.desember(2021)), Uføregrad.parse(40)),
        )
    }

    @Test
    fun `2 uføregrunnlag tilstøter og er lik`() {
        val u1 = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = januar(2021),
            uføregrad = Uføregrad.parse(50),
            forventetInntekt = 50,
        )

        val u2 = u1.copy(
            periode = februar(2021),
        )

        u1.tilstøterOgErLik(u2) shouldBe true
    }

    @Test
    fun `2 uføregrunnlag ikke tilstøter, men er lik`() {
        val u1 = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = januar(2021),
            uføregrad = Uføregrad.parse(50),
            forventetInntekt = 50,
        )

        val u2 = u1.copy(
            periode = mars(2021),
        )

        u1.tilstøterOgErLik(u2) shouldBe false
    }

    @Test
    fun `2 uføregrunnlag tilstøter, men er uføregrad er ulik`() {
        val u1 = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = januar(2021),
            uføregrad = Uføregrad.parse(50),
            forventetInntekt = 50,
        )

        val u2 = u1.copy(
            periode = februar(2021),
            uføregrad = Uføregrad.parse(20),
        )

        u1.tilstøterOgErLik(u2) shouldBe false
    }

    @Test
    fun `2 uføregrunnlag tilstøter, men er forventet inntekt er ulik`() {
        val u1 = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = januar(2021),
            uføregrad = Uføregrad.parse(50),
            forventetInntekt = 50,
        )

        val u2 = u1.copy(
            periode = februar(2021),
            forventetInntekt = 200,
        )

        u1.tilstøterOgErLik(u2) shouldBe false
    }

    @Test
    fun `2 uføregrunnlag som ikke tilstøter, og er ulik`() {
        val u1 = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = januar(2021),
            uføregrad = Uføregrad.parse(50),
            forventetInntekt = 50,
        )

        val u2 = u1.copy(
            periode = mars(2021),
            forventetInntekt = 200,
        )

        u1.tilstøterOgErLik(u2) shouldBe false
    }
}
