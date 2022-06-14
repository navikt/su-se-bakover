package no.nav.su.se.bakover.domain.vilkår

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpForMåned
import no.nav.su.se.bakover.domain.satser.Faktor
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

internal class FormuegrenserFactoryTest {

    private val formuegrense = formuegrenserFactoryTestPåDato(LocalDate.now())

    @Nested
    inner class `forMåned()` {

        @Test
        fun `januar 2021`() {
            formuegrense.forMåned(januar(2021)) shouldBe FormuegrenseForMåned(
                grunnbeløpForMåned = GrunnbeløpForMåned(
                    måned = januar(2021),
                    grunnbeløpPerÅr = 101351,
                    ikrafttredelse = 4.september(2020),
                    virkningstidspunkt = 1.mai(2020),
                ),
                faktor = Faktor(0.5),
            ).also {
                it.virkningstidspunkt shouldBe 1.mai(2020)
                it.formuegrense shouldBe BigDecimal("50675.5")
                it.formuegrenseMedToDesimaler shouldBe 50675.50
            }
        }

        @Test
        fun `februar 2021`() {
            formuegrense.forMåned(februar(2021)) shouldBe FormuegrenseForMåned(
                grunnbeløpForMåned = GrunnbeløpForMåned(
                    måned = februar(2021),
                    grunnbeløpPerÅr = 101351,
                    ikrafttredelse = 4.september(2020),
                    virkningstidspunkt = 1.mai(2020),
                ),
                faktor = Faktor(0.5),
            )
        }

        @Test
        fun `mars 2021`() {
            formuegrense.forMåned(mars(2021)) shouldBe FormuegrenseForMåned(
                grunnbeløpForMåned = GrunnbeløpForMåned(
                    måned = mars(2021),
                    grunnbeløpPerÅr = 101351,
                    ikrafttredelse = 4.september(2020),
                    virkningstidspunkt = 1.mai(2020),
                ),
                faktor = Faktor(0.5),
            )
        }

        @Test
        fun `april 2021`() {
            formuegrense.forMåned(april(2021)) shouldBe FormuegrenseForMåned(
                grunnbeløpForMåned = GrunnbeløpForMåned(
                    måned = april(2021),
                    grunnbeløpPerÅr = 101351,
                    ikrafttredelse = 4.september(2020),
                    virkningstidspunkt = 1.mai(2020),
                ),
                faktor = Faktor(0.5),
            )
        }

        @Test
        fun `mai 2021`() {
            formuegrense.forMåned(mai(2021)) shouldBe FormuegrenseForMåned(
                grunnbeløpForMåned = GrunnbeløpForMåned(
                    måned = mai(2021),
                    grunnbeløpPerÅr = 106399,
                    ikrafttredelse = 21.mai(2021),
                    virkningstidspunkt = 1.mai(2021),
                ),
                faktor = Faktor(0.5),
            ).also {
                it.virkningstidspunkt shouldBe 1.mai(2021)
                it.formuegrense shouldBe BigDecimal("53199.5")
            }
        }

        @Test
        fun `mai 2022`() {
            formuegrense.forMåned(mai(2022)) shouldBe FormuegrenseForMåned(
                grunnbeløpForMåned = GrunnbeløpForMåned(
                    måned = mai(2022),
                    grunnbeløpPerÅr = 111477,
                    ikrafttredelse = 20.mai(2022),
                    virkningstidspunkt = 1.mai(2022),
                ),
                faktor = Faktor(0.5),
            )
        }
    }

    @Nested
    inner class `virkningstidspunkt()` {
        @Test
        fun `virkningstidspunkt fra mai 2005`() {
            formuegrenserFactoryTestPåDato(LocalDate.now()).virkningstidspunkt(
                YearMonth.of(
                    2005,
                    Month.MAY,
                ),
            ) shouldBe listOf(
                1.mai(2022) to BigDecimal("55738.5"),
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
        fun `virkningstidspunkt fra mai 2021`() {
            formuegrenserFactoryTestPåDato(LocalDate.now()).virkningstidspunkt(
                YearMonth.of(
                    2021,
                    Month.MAY,
                ),
            ) shouldBe listOf(
                1.mai(2022) to BigDecimal("55738.5"),
                1.mai(2021) to BigDecimal("53199.5"),
            )
        }

        @Test
        fun `virkningstidspunkt fra mai 2022`() {
            formuegrenserFactoryTestPåDato(LocalDate.now()).virkningstidspunkt(
                YearMonth.of(
                    2022,
                    Month.MAY,
                ),
            ) shouldBe listOf(
                1.mai(2022) to BigDecimal("55738.5"),
            )
        }

        @Test
        fun `virkningstidspunkt fra mai 2023`() {
            formuegrenserFactoryTestPåDato(LocalDate.now()).virkningstidspunkt(
                YearMonth.of(
                    2023,
                    Month.MAY,
                ),
            ) shouldBe listOf(
                1.mai(2022) to BigDecimal("55738.5"),
            )
        }

        @Test
        fun `Ikrafttredelse januar 2021`() {
            satsFactoryTestPåDato(
                påDato = 1.januar(2021),
            ).formuegrenserFactory.virkningstidspunkt(
                fraOgMed = YearMonth.of(2021, Month.JANUARY),
            ) shouldBe listOf(1.mai(2020) to BigDecimal("50675.5"))
        }

        @Test
        fun `Ikrafttredelse april 2021`() {
            satsFactoryTestPåDato(
                påDato = 1.april(2021),
            ).formuegrenserFactory.virkningstidspunkt(
                fraOgMed = YearMonth.of(2021, Month.JANUARY),
            ) shouldBe listOf(1.mai(2020) to BigDecimal("50675.5"))
        }

        @Test
        fun `Ikrafttredelse mai 2021`() {
            satsFactoryTestPåDato(
                påDato = 21.mai(2021),
            ).formuegrenserFactory.virkningstidspunkt(
                fraOgMed = YearMonth.of(2021, Month.JANUARY),
            ) shouldBe listOf(1.mai(2021) to BigDecimal("53199.5"), 1.mai(2020) to BigDecimal("50675.5"))
        }

        @Test
        fun `Ikrafttredelse april 2022`() {
            satsFactoryTestPåDato(
                påDato = 1.april(2022),
            ).formuegrenserFactory.virkningstidspunkt(
                fraOgMed = YearMonth.of(2021, Month.JANUARY),
            ) shouldBe listOf(1.mai(2021) to BigDecimal("53199.5"), 1.mai(2020) to BigDecimal("50675.5"))
        }

        @Test
        fun `Ikrafttredelse mai 2022`() {
            satsFactoryTestPåDato(
                påDato = 20.mai(2022),
            ).formuegrenserFactory.virkningstidspunkt(
                fraOgMed = YearMonth.of(2021, Month.JANUARY),
            ) shouldBe listOf(
                1.mai(2022) to BigDecimal("55738.5"),
                1.mai(2021) to BigDecimal("53199.5"),
                1.mai(2020) to BigDecimal("50675.5"),
            )
        }
    }
}
