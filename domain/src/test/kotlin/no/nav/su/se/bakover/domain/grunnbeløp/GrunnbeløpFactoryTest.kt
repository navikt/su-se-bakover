package no.nav.su.se.bakover.domain.grunnbeløp

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.mai
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class GrunnbeløpFactoryTest {

    private val factory = GrunnbeløpFactory.createFromGrunnbeløp(
        grunnbeløp = listOf(
            1.mai(2005) to 60699,
            1.mai(2006) to 62892,
            1.mai(2007) to 66812,
            1.mai(2008) to 70256,
            1.mai(2009) to 72881,
            1.mai(2010) to 75641,
            1.mai(2011) to 79216,
            1.mai(2012) to 82122,
            1.mai(2013) to 85245,
            1.mai(2014) to 88370,
            1.mai(2015) to 90068,
            1.mai(2016) to 92576,
            1.mai(2017) to 93634,
            1.mai(2018) to 96883,
            1.mai(2019) to 99858,
            1.mai(2020) to 101351,
            1.mai(2021) to 106399,
        ),
    )

    @Test
    fun `april 2017`() {
        factory.forMåned(april(2017)) shouldBe GrunnbeløpForMåned(
            måned = april(2017),
            grunnbeløpPerÅr = 92576,
            ikrafttredelse = 1.mai(2016),
        ).also {
            it.halvtGrunnbeløpPerÅrAvrundet() shouldBe 46288
        }
    }

    @Test
    fun `mai 2017`() {
        factory.forMåned(mai(2017)) shouldBe GrunnbeløpForMåned(
            måned = mai(2017),
            grunnbeløpPerÅr = 93634,
            ikrafttredelse = 1.mai(2017),
        ).also {
            it.halvtGrunnbeløpPerÅrAvrundet() shouldBe 46817
        }
    }

    @Test
    fun `april 2018`() {
        factory.forMåned(april(2018)) shouldBe GrunnbeløpForMåned(
            måned = april(2018),
            grunnbeløpPerÅr = 93634,
            ikrafttredelse = 1.mai(2017),
        )
    }

    @Test
    fun `mai 2018`() {
        factory.forMåned(mai(2018)) shouldBe GrunnbeløpForMåned(
            måned = mai(2018),
            grunnbeløpPerÅr = 96883,
            ikrafttredelse = 1.mai(2018),
        )
    }

    @Test
    fun `april 2019`() {
        factory.forMåned(april(2019)) shouldBe GrunnbeløpForMåned(
            måned = april(2019),
            grunnbeløpPerÅr = 96883,
            ikrafttredelse = 1.mai(2018),
        )
    }

    @Test
    fun `mai 2019`() {
        factory.forMåned(mai(2019)) shouldBe GrunnbeløpForMåned(
            måned = mai(2019),
            grunnbeløpPerÅr = 99858,
            ikrafttredelse = 1.mai(2019),
        )
    }

    @Test
    fun `april 2020`() {
        factory.forMåned(april(2020)) shouldBe GrunnbeløpForMåned(
            måned = april(2020),
            grunnbeløpPerÅr = 99858,
            ikrafttredelse = 1.mai(2019),
        )
    }

    @Test
    fun `mai 2020`() {
        factory.forMåned(mai(2020)) shouldBe GrunnbeløpForMåned(
            måned = mai(2020),
            grunnbeløpPerÅr = 101351,
            ikrafttredelse = 1.mai(2020),
        )
    }

    @Test
    fun `april 2021`() {
        factory.forMåned(april(2021)) shouldBe GrunnbeløpForMåned(
            måned = april(2021),
            grunnbeløpPerÅr = 101351,
            ikrafttredelse = 1.mai(2020),
        )
    }

    @Test
    fun `mai 2021`() {
        factory.forMåned(mai(2021)) shouldBe GrunnbeløpForMåned(
            måned = mai(2021),
            grunnbeløpPerÅr = 106399,
            ikrafttredelse = 1.mai(2021),
        )
    }

    @Test
    fun `april 2022`() {
        factory.forMåned(april(2022)) shouldBe GrunnbeløpForMåned(
            måned = april(2022),
            grunnbeløpPerÅr = 106399,
            ikrafttredelse = 1.mai(2021),
        )
    }

    @Test
    fun `historisk grunnbeløp`() {
        factory.gjeldende(1.januar(2020)).forMåned(mai(2022)) shouldBe GrunnbeløpForMåned(
            måned = mai(2022),
            grunnbeløpPerÅr = 99858,
            ikrafttredelse = 1.mai(2019),
        )

        factory.gjeldende(1.januar(2021)).forMåned(mai(2022)) shouldBe GrunnbeløpForMåned(
            måned = mai(2022),
            grunnbeløpPerÅr = 101351,
            ikrafttredelse = 1.mai(2020),
        )

        factory.gjeldende(1.januar(2022)).alle().count() shouldBe factory.alle().count()

        factory.gjeldende(LocalDate.now()).alle() shouldBe factory.alle()
    }
}
