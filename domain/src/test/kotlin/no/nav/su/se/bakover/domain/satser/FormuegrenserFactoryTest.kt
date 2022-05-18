package no.nav.su.se.bakover.domain.satser

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.april
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.januar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.domain.grunnbeløp.GrunnbeløpForMåned
import no.nav.su.se.bakover.domain.vilkår.FormuegrenseForMåned
import no.nav.su.se.bakover.test.formuegrenserFactoryTest
import org.junit.jupiter.api.Test
import java.math.BigDecimal

internal class FormuegrenserFactoryTest {

    private val formuegrense = formuegrenserFactoryTest

    @Test
    fun `januar 2021`() {
        formuegrense.forMåned(januar(2021)) shouldBe FormuegrenseForMåned(
            grunnbeløpForMåned = GrunnbeløpForMåned(
                måned = januar(2021),
                grunnbeløpPerÅr = 101351,
                ikrafttredelse = 1.mai(2020),
                virkningstidspunkt = 1.mai(2020),
            ),
            faktor = Faktor(0.5),
        )
    }

    @Test
    fun `februar 2021`() {
        formuegrense.forMåned(februar(2021)) shouldBe FormuegrenseForMåned(
            grunnbeløpForMåned = GrunnbeløpForMåned(
                måned = februar(2021),
                grunnbeløpPerÅr = 101351,
                ikrafttredelse = 1.mai(2020),
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
                ikrafttredelse = 1.mai(2020),
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
                ikrafttredelse = 1.mai(2020),
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
                ikrafttredelse = 1.mai(2021),
                virkningstidspunkt = 1.mai(2021),
            ),
            faktor = Faktor(0.5),
        ).also {
            it.ikrafttredelse shouldBe 1.mai(2021)
            it.formuegrense shouldBe BigDecimal("53199.5")
        }
    }

    @Test
    fun `mai 2022`() {
        formuegrense.forMåned(mai(2022)) shouldBe FormuegrenseForMåned(
            grunnbeløpForMåned = GrunnbeløpForMåned(
                måned = mai(2022),
                grunnbeløpPerÅr = 107099,
                ikrafttredelse = 1.mai(2022),
                virkningstidspunkt = 1.mai(2022),
            ),
            faktor = Faktor(0.5),
        )
    }
}
