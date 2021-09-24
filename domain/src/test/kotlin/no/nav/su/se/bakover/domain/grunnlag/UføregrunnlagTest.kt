package no.nav.su.se.bakover.domain.grunnlag

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfDay
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.fixedTidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Uføregrunnlag.Companion.slåSammenPeriodeOgUføregrad
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.ZoneOffset
import java.util.UUID

internal class UføregrunnlagTest {
    private val fixedClock: Clock = Clock.fixed(1.januar(2021).startOfDay().instant, ZoneOffset.UTC)

    @Test
    fun `bevarer korrekte verdier ved kopiering for plassering på tidslinje - full kopi`() {
        val original = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            uføregrad = Uføregrad.parse(50),
            forventetInntekt = 500
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
            opprettet = Tidspunkt.now(fixedClock),
            periode = Periode.create(1.januar(2021), 31.desember(2021)),
            uføregrad = Uføregrad.parse(50),
            forventetInntekt = 500
        )
        original.copy(
            CopyArgs.Tidslinje.NyPeriode(
                periode = Periode.create(1.mai(2021), 31.desember(2021))
            )
        ).let {
            it.id shouldNotBe original.id
            it.opprettet shouldBe original.opprettet
            it.periode shouldBe Periode.create(1.mai(2021), 31.desember(2021))
            it.uføregrad shouldBe original.uføregrad
            it.forventetInntekt shouldBe original.forventetInntekt
        }
    }

    @Test
    fun `liste med 1 uføregrunnlag returnerer sin periode og uføregrad`() {
        val periode = Periode.create(fraOgMed = 1.januar(2021), tilOgMed = 31.desember(2021))
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
            periode = Periode.create(fraOgMed = 1.mai(2021), tilOgMed = 31.mai(2021)),
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
}
