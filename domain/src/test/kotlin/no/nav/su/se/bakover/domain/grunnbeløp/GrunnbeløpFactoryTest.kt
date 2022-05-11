package no.nav.su.se.bakover.domain.grunnbeløp

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.test.satsFactoryTest
import org.junit.jupiter.api.Test

internal class GrunnbeløpFactoryTest {

    @Test
    fun `april 2017`() {
        satsFactoryTest.grunnbeløp(april(2017)) shouldBe GrunnbeløpForMåned(
            måned = april(2017),
            grunnbeløpPerÅr = 92576,
            ikrafttredelse = 1.mai(2016),
        ).also {
            it.halvtGrunnbeløpPerÅrAvrundet() shouldBe 46288
        }
    }

    @Test
    fun `mai 2017`() {
        satsFactoryTest.grunnbeløp(mai(2017)) shouldBe GrunnbeløpForMåned(
            måned = mai(2017),
            grunnbeløpPerÅr = 93634,
            ikrafttredelse = 1.mai(2017),
        ).also {
            it.halvtGrunnbeløpPerÅrAvrundet() shouldBe 46817
        }
    }

    @Test
    fun `april 2018`() {
        satsFactoryTest.grunnbeløp(april(2018)) shouldBe GrunnbeløpForMåned(
            måned = april(2018),
            grunnbeløpPerÅr = 93634,
            ikrafttredelse = 1.mai(2017),
        )
    }

    @Test
    fun `mai 2018`() {
        satsFactoryTest.grunnbeløp(mai(2018)) shouldBe GrunnbeløpForMåned(
            måned = mai(2018),
            grunnbeløpPerÅr = 96883,
            ikrafttredelse = 1.mai(2018),
        )
    }

    @Test
    fun `april 2019`() {
        satsFactoryTest.grunnbeløp(april(2019)) shouldBe GrunnbeløpForMåned(
            måned = april(2019),
            grunnbeløpPerÅr = 96883,
            ikrafttredelse = 1.mai(2018),
        )
    }

    @Test
    fun `mai 2019`() {
        satsFactoryTest.grunnbeløp(mai(2019)) shouldBe GrunnbeløpForMåned(
            måned = mai(2019),
            grunnbeløpPerÅr = 99858,
            ikrafttredelse = 1.mai(2019),
        )
    }

    @Test
    fun `april 2020`() {
        satsFactoryTest.grunnbeløp(april(2020)) shouldBe GrunnbeløpForMåned(
            måned = april(2020),
            grunnbeløpPerÅr = 99858,
            ikrafttredelse = 1.mai(2019),
        )
    }

    @Test
    fun `mai 2020`() {
        satsFactoryTest.grunnbeløp(mai(2020)) shouldBe GrunnbeløpForMåned(
            måned = mai(2020),
            grunnbeløpPerÅr = 101351,
            ikrafttredelse = 1.mai(2020),
        )
    }

    @Test
    fun `april 2021`() {
        satsFactoryTest.grunnbeløp(april(2021)) shouldBe GrunnbeløpForMåned(
            måned = april(2021),
            grunnbeløpPerÅr = 101351,
            ikrafttredelse = 1.mai(2020),
        )
    }

    @Test
    fun `mai 2021`() {
        satsFactoryTest.grunnbeløp(mai(2021)) shouldBe GrunnbeløpForMåned(
            måned = mai(2021),
            grunnbeløpPerÅr = 106399,
            ikrafttredelse = 1.mai(2021),
        )
    }

    @Test
    fun `april 2022`() {
        satsFactoryTest.grunnbeløp(april(2022)) shouldBe GrunnbeløpForMåned(
            måned = april(2022),
            grunnbeløpPerÅr = 106399,
            ikrafttredelse = 1.mai(2021),
        )
    }

    @Test
    fun `mai 2022`() {
        satsFactoryTest.grunnbeløp(mai(2022)) shouldBe GrunnbeløpForMåned(
            måned = mai(2022),
            grunnbeløpPerÅr = 107099,
            ikrafttredelse = 1.mai(2022),
        )
    }
}
