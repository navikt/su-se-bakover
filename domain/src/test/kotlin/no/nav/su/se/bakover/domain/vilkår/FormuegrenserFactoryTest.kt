package no.nav.su.se.bakover.domain.vilkår

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpForMåned
import no.nav.su.se.bakover.domain.satser.Faktor
import no.nav.su.se.bakover.test.formuegrenserFactoryTest
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Clock
import java.time.Month
import java.time.YearMonth

internal class FormuegrenserFactoryTest {

    @Test
    fun `ikrafttredelser fra mai 2005`() {
        formuegrenserFactoryTest(Clock.systemUTC()).ikrafttredelser(YearMonth.of(2005, Month.MAY)) shouldBe listOf(
            1.mai(2022) to BigDecimal("53549.5"),
            1.mai(2021) to BigDecimal("53199.5"),
            1.mai(2020) to BigDecimal("50675.5"),
            1.mai(2019) to BigDecimal("49929.0"),
            1.mai(2018) to BigDecimal("48441.5"),
            1.mai(2017) to BigDecimal("46817.0"),
            1.mai(2016) to BigDecimal("46288.0"),
            1.mai(2015) to BigDecimal("45034.0"),
            1.mai(2014) to BigDecimal("44185.0"),
            1.mai(2013) to BigDecimal("42622.5"),
            1.mai(2012) to BigDecimal("41061.0"),
            1.mai(2011) to BigDecimal("39608.0"),
            1.mai(2010) to BigDecimal("37820.5"),
            1.mai(2009) to BigDecimal("36440.5"),
            1.mai(2008) to BigDecimal("35128.0"),
            1.mai(2007) to BigDecimal("33406.0"),
            1.mai(2006) to BigDecimal("31446.0"),
            1.mai(2005) to BigDecimal("30349.5"),
        )
    }

    @Test
    fun `ikrafttredelser fra mai 2021`() {
        formuegrenserFactoryTest(Clock.systemUTC()).ikrafttredelser(YearMonth.of(2021, Month.MAY)) shouldBe listOf(
            1.mai(2022) to BigDecimal("53549.5"),
            1.mai(2021) to BigDecimal("53199.5"),
        )
    }

    @Test
    fun `ikrafttredelser fra mai 2022`() {
        formuegrenserFactoryTest(Clock.systemUTC()).ikrafttredelser(YearMonth.of(2022, Month.MAY)) shouldBe listOf(
            1.mai(2022) to BigDecimal("53549.5"),
        )
    }

    @Test
    fun `ikrafttredelser fra mai 2023`() {
        formuegrenserFactoryTest(Clock.systemUTC()).ikrafttredelser(YearMonth.of(2023, Month.MAY)) shouldBe emptyList()
    }

    @Test
    fun `januar 2021`() {
        formuegrenserFactoryTest(Clock.systemUTC()).forMåned(januar(2021)) shouldBe FormuegrenseForMåned(
            grunnbeløpForMåned = GrunnbeløpForMåned(
                måned = januar(2021),
                grunnbeløpPerÅr = 101351,
                ikrafttredelse = 1.mai(2020),
                virkningstidspunkt = 1.mai(2020),
            ),
            faktor = Faktor(0.5),
        ).also {
            it.ikrafttredelse shouldBe 1.mai(2020)
            it.formuegrense shouldBe BigDecimal("50675.5")
            it.formuegrenseMedToDesimaler shouldBe 50675.50
        }
    }
}
