package no.nav.su.se.bakover.domain.grunnbeløp

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.mai
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate

internal class GrunnbeløpFactoryTest {

    private val factory = GrunnbeløpFactory(
        clock = Clock.systemUTC(),
        grunnbeløpsendringer = listOf(
            Grunnbeløpsendring(1.mai(2005), 1.mai(2005), 60699),
            Grunnbeløpsendring(1.mai(2006), 1.mai(2006), 62892),
            Grunnbeløpsendring(1.mai(2007), 1.mai(2007), 66812),
            Grunnbeløpsendring(1.mai(2008), 1.mai(2008), 70256),
            Grunnbeløpsendring(1.mai(2009), 1.mai(2009), 72881),
            Grunnbeløpsendring(1.mai(2010), 1.mai(2010), 75641),
            Grunnbeløpsendring(1.mai(2011), 1.mai(2011), 79216),
            Grunnbeløpsendring(1.mai(2012), 1.mai(2012), 82122),
            Grunnbeløpsendring(1.mai(2013), 1.mai(2013), 85245),
            Grunnbeløpsendring(1.mai(2014), 1.mai(2014), 88370),
            Grunnbeløpsendring(1.mai(2015), 1.mai(2015), 90068),
            Grunnbeløpsendring(1.mai(2016), 1.mai(2016), 92576),
            Grunnbeløpsendring(1.mai(2017), 1.mai(2017), 93634),
            Grunnbeløpsendring(1.mai(2018), 1.mai(2018), 96883),
            Grunnbeløpsendring(1.mai(2019), 1.mai(2019), 99858),
            Grunnbeløpsendring(1.mai(2020), 1.mai(2020), 101351),
            Grunnbeløpsendring(1.mai(2021), 1.mai(2021), 106399),
        ),
    )

    @Test
    fun `april 2017`() {
        factory.forMåned(april(2017)) shouldBe GrunnbeløpForMåned(
            måned = april(2017),
            grunnbeløpPerÅr = 92576,
            ikrafttredelse = 1.mai(2016),
            virkningstidspunkt = 1.mai(2016),
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
            virkningstidspunkt = 1.mai(2017),
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
            virkningstidspunkt = 1.mai(2017),
        )
    }

    @Test
    fun `mai 2018`() {
        factory.forMåned(mai(2018)) shouldBe GrunnbeløpForMåned(
            måned = mai(2018),
            grunnbeløpPerÅr = 96883,
            ikrafttredelse = 1.mai(2018),
            virkningstidspunkt = 1.mai(2018),
        )
    }

    @Test
    fun `april 2019`() {
        factory.forMåned(april(2019)) shouldBe GrunnbeløpForMåned(
            måned = april(2019),
            grunnbeløpPerÅr = 96883,
            ikrafttredelse = 1.mai(2018),
            virkningstidspunkt = 1.mai(2018),
        )
    }

    @Test
    fun `mai 2019`() {
        factory.forMåned(mai(2019)) shouldBe GrunnbeløpForMåned(
            måned = mai(2019),
            grunnbeløpPerÅr = 99858,
            ikrafttredelse = 1.mai(2019),
            virkningstidspunkt = 1.mai(2019),
        )
    }

    @Test
    fun `april 2020`() {
        factory.forMåned(april(2020)) shouldBe GrunnbeløpForMåned(
            måned = april(2020),
            grunnbeløpPerÅr = 99858,
            ikrafttredelse = 1.mai(2019),
            virkningstidspunkt = 1.mai(2019),
        )
    }

    @Test
    fun `mai 2020`() {
        factory.forMåned(mai(2020)) shouldBe GrunnbeløpForMåned(
            måned = mai(2020),
            grunnbeløpPerÅr = 101351,
            ikrafttredelse = 1.mai(2020),
            virkningstidspunkt = 1.mai(2020),
        )
    }

    @Test
    fun `april 2021`() {
        factory.forMåned(april(2021)) shouldBe GrunnbeløpForMåned(
            måned = april(2021),
            grunnbeløpPerÅr = 101351,
            ikrafttredelse = 1.mai(2020),
            virkningstidspunkt = 1.mai(2020),
        )
    }

    @Test
    fun `mai 2021`() {
        factory.forMåned(mai(2021)) shouldBe GrunnbeløpForMåned(
            måned = mai(2021),
            grunnbeløpPerÅr = 106399,
            ikrafttredelse = 1.mai(2021),
            virkningstidspunkt = 1.mai(2021),
        )
    }

    @Test
    fun `april 2022`() {
        factory.forMåned(april(2022)) shouldBe GrunnbeløpForMåned(
            måned = april(2022),
            grunnbeløpPerÅr = 106399,
            ikrafttredelse = 1.mai(2021),
            virkningstidspunkt = 1.mai(2021),
        )
    }

    @Test
    fun `historisk grunnbeløp`() {
        factory.gjeldende(1.januar(2020)).forMåned(mai(2022)) shouldBe GrunnbeløpForMåned(
            måned = mai(2022),
            grunnbeløpPerÅr = 99858,
            ikrafttredelse = 1.mai(2019),
            virkningstidspunkt = 1.mai(2019),
        )

        factory.gjeldende(1.januar(2021)).forMåned(mai(2022)) shouldBe GrunnbeløpForMåned(
            måned = mai(2022),
            grunnbeløpPerÅr = 101351,
            ikrafttredelse = 1.mai(2020),
            virkningstidspunkt = 1.mai(2020),
        )

        factory.gjeldende(1.januar(2022)).alle().count() shouldBe factory.alle().count()

        factory.gjeldende(LocalDate.now()).alle() shouldBe factory.alle()
    }
}
