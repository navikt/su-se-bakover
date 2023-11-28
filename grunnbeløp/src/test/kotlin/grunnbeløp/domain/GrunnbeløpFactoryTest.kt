package grunnbeløp.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Knekkpunkt
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.september
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import org.junit.jupiter.api.Test
import satser.domain.supplerendestønad.grunnbeløpsendringer

internal class GrunnbeløpFactoryTest {

    private val juni22 = GrunnbeløpFactory(
        tidligsteTilgjengeligeMåned = januar(2016),
        knekkpunkt = Knekkpunkt(1.juni(2022)),
        grunnbeløpsendringer = grunnbeløpsendringer,
    )

    @Test
    fun `april 2017`() {
        juni22.forMåned(april(2017)) shouldBe GrunnbeløpForMåned(
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
        juni22.forMåned(mai(2017)) shouldBe GrunnbeløpForMåned(
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
        juni22.forMåned(april(2018)) shouldBe GrunnbeløpForMåned(
            måned = april(2018),
            grunnbeløpPerÅr = 93634,
            ikrafttredelse = 1.mai(2017),
            virkningstidspunkt = 1.mai(2017),
        )
    }

    @Test
    fun `mai 2018`() {
        juni22.forMåned(mai(2018)) shouldBe GrunnbeløpForMåned(
            måned = mai(2018),
            grunnbeløpPerÅr = 96883,
            ikrafttredelse = 1.mai(2018),
            virkningstidspunkt = 1.mai(2018),
        )
    }

    @Test
    fun `april 2019`() {
        juni22.forMåned(april(2019)) shouldBe GrunnbeløpForMåned(
            måned = april(2019),
            grunnbeløpPerÅr = 96883,
            ikrafttredelse = 1.mai(2018),
            virkningstidspunkt = 1.mai(2018),
        )
    }

    @Test
    fun `mai 2019`() {
        juni22.forMåned(mai(2019)) shouldBe GrunnbeløpForMåned(
            måned = mai(2019),
            grunnbeløpPerÅr = 99858,
            ikrafttredelse = 1.mai(2019),
            virkningstidspunkt = 1.mai(2019),
        )
    }

    @Test
    fun `april 2020`() {
        juni22.forMåned(april(2020)) shouldBe GrunnbeløpForMåned(
            måned = april(2020),
            grunnbeløpPerÅr = 99858,
            ikrafttredelse = 1.mai(2019),
            virkningstidspunkt = 1.mai(2019),
        )
    }

    @Test
    fun `mai 2020`() {
        juni22.forMåned(mai(2020)) shouldBe GrunnbeløpForMåned(
            måned = mai(2020),
            grunnbeløpPerÅr = 101351,
            ikrafttredelse = 4.september(2020),
            virkningstidspunkt = 1.mai(2020),
        )
    }

    @Test
    fun `april 2021`() {
        juni22.forMåned(april(2021)) shouldBe GrunnbeløpForMåned(
            måned = april(2021),
            grunnbeløpPerÅr = 101351,
            ikrafttredelse = 4.september(2020),
            virkningstidspunkt = 1.mai(2020),
        )
    }

    @Test
    fun `mai 2021`() {
        juni22.forMåned(mai(2021)) shouldBe GrunnbeløpForMåned(
            måned = mai(2021),
            grunnbeløpPerÅr = 106399,
            ikrafttredelse = 21.mai(2021),
            virkningstidspunkt = 1.mai(2021),
        )
    }

    @Test
    fun `april 2022`() {
        juni22.forMåned(april(2022)) shouldBe GrunnbeløpForMåned(
            måned = april(2022),
            grunnbeløpPerÅr = 106399,
            ikrafttredelse = 21.mai(2021),
            virkningstidspunkt = 1.mai(2021),
        )
    }

    @Test
    fun `historisk grunnbeløp`() {
        GrunnbeløpFactory(
            tidligsteTilgjengeligeMåned = januar(2016),
            knekkpunkt = Knekkpunkt(1.januar(2020)),
            grunnbeløpsendringer = grunnbeløpsendringer,
        ).forMåned(mai(2022)) shouldBe GrunnbeløpForMåned(
            måned = mai(2022),
            grunnbeløpPerÅr = 99858,
            ikrafttredelse = 1.mai(2019),
            virkningstidspunkt = 1.mai(2019),
        )
        GrunnbeløpFactory(
            tidligsteTilgjengeligeMåned = januar(2016),
            knekkpunkt = Knekkpunkt(1.januar(2021)),
            grunnbeløpsendringer = grunnbeløpsendringer,
        ).forMåned(mai(2022)) shouldBe GrunnbeløpForMåned(
            måned = mai(2022),
            grunnbeløpPerÅr = 101351,
            ikrafttredelse = 4.september(2020),
            virkningstidspunkt = 1.mai(2020),
        )
    }
}
