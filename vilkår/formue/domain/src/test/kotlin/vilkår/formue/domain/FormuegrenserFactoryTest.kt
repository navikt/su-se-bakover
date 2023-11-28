package vilkår.formue.domain

import grunnbeløp.domain.GrunnbeløpFactory
import grunnbeløp.domain.GrunnbeløpForMåned
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Faktor
import no.nav.su.se.bakover.common.domain.Knekkpunkt
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.september
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.test.formuegrenserFactoryTestPåDato
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import satser.domain.supplerendestønad.grunnbeløpsendringer
import java.math.BigDecimal

internal class FormuegrenserFactoryTest {

    private val formuegrense = formuegrenserFactoryTestPåDato(1.juni(2022))

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
        fun `virkningstidspunkt fra januar 2016`() {
            val grunnbeløpFactory = GrunnbeløpFactory(
                tidligsteTilgjengeligeMåned = januar(2016),
                knekkpunkt = Knekkpunkt(1.juni(2022)),
                grunnbeløpsendringer = grunnbeløpsendringer,
            )
            val formuegrenserFactory = FormuegrenserFactory.createFromGrunnbeløp(
                grunnbeløpFactory = grunnbeløpFactory,
                tidligsteTilgjengeligeMåned = januar(2016),
            )

            formuegrenserFactory.virkningstidspunkt(
                januar(2016),
            ) shouldBe listOf(
                1.mai(2022) to BigDecimal("55738.5"),
                1.mai(2021) to BigDecimal("53199.5"),
                1.mai(2020) to BigDecimal("50675.5"),
                1.mai(2019) to BigDecimal("49929.0"),
                1.mai(2018) to BigDecimal("48441.5"),
                1.mai(2017) to BigDecimal("46817.0"),
                1.mai(2016) to BigDecimal("46288.0"),
                1.mai(2015) to BigDecimal("45034.0"),
                // TODO jah: Disse har gått igjennom verifikasjon. De skal nok inn igjen når vi skal begynne å revurdere alder.
                // 1.mai(2014) to BigDecimal("44185.0"),
                // 1.mai(2013) to BigDecimal("42622.5"),
                // 1.mai(2012) to BigDecimal("41061.0"),
                // 1.mai(2011) to BigDecimal("39608.0"),
                // 1.mai(2010) to BigDecimal("37820.5"),
                // 1.mai(2009) to BigDecimal("36440.5"),
                // 1.mai(2008) to BigDecimal("35128.0"),
                // 1.mai(2007) to BigDecimal("33406.0"),
                // 1.mai(2006) to BigDecimal("31446.0"),
                // 1.mai(2005) to BigDecimal("30349.5"),
            )
        }

        @Test
        fun `virkningstidspunkt fra mai 2021`() {
            formuegrenserFactoryTestPåDato(1.juni(2022)).virkningstidspunkt(
                mai(2021),
            ) shouldBe listOf(
                1.mai(2022) to BigDecimal("55738.5"),
                1.mai(2021) to BigDecimal("53199.5"),
            )
        }

        @Test
        fun `virkningstidspunkt fra mai 2022`() {
            formuegrenserFactoryTestPåDato(1.juni(2022)).virkningstidspunkt(
                mai(2022),
            ) shouldBe listOf(
                1.mai(2022) to BigDecimal("55738.5"),
            )
        }

        @Test
        fun `virkningstidspunkt fra mai 2023`() {
            formuegrenserFactoryTestPåDato(1.juni(2022)).virkningstidspunkt(
                mai(2023),
            ) shouldBe listOf(
                1.mai(2022) to BigDecimal("55738.5"),
            )
        }

        @Test
        fun `Ikrafttredelse januar 2021`() {
            formuegrenserFactoryTestPåDato(
                påDato = 1.januar(2021),
            ).virkningstidspunkt(
                fraOgMed = januar(2021),
            ) shouldBe listOf(1.mai(2020) to BigDecimal("50675.5"))
        }

        @Test
        fun `Ikrafttredelse april 2021`() {
            formuegrenserFactoryTestPåDato(
                påDato = 1.april(2021),
            ).virkningstidspunkt(
                fraOgMed = januar(2021),
            ) shouldBe listOf(1.mai(2020) to BigDecimal("50675.5"))
        }

        @Test
        fun `Ikrafttredelse mai 2021`() {
            formuegrenserFactoryTestPåDato(
                påDato = 21.mai(2021),
            ).virkningstidspunkt(
                fraOgMed = januar(2021),
            ) shouldBe listOf(1.mai(2021) to BigDecimal("53199.5"), 1.mai(2020) to BigDecimal("50675.5"))
        }

        @Test
        fun `Ikrafttredelse april 2022`() {
            formuegrenserFactoryTestPåDato(
                påDato = 1.april(2022),
            ).virkningstidspunkt(
                fraOgMed = januar(2021),
            ) shouldBe listOf(1.mai(2021) to BigDecimal("53199.5"), 1.mai(2020) to BigDecimal("50675.5"))
        }

        @Test
        fun `Ikrafttredelse mai 2022`() {
            formuegrenserFactoryTestPåDato(
                påDato = 20.mai(2022),
            ).virkningstidspunkt(
                fraOgMed = januar(2021),
            ) shouldBe listOf(
                1.mai(2022) to BigDecimal("55738.5"),
                1.mai(2021) to BigDecimal("53199.5"),
                1.mai(2020) to BigDecimal("50675.5"),
            )
        }
    }
}
