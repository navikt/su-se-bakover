package no.nav.su.se.bakover.common.periode

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class PeriodeTest {
    @Test
    fun `fra og med og til og med`() {
        val periode = Periode(1.januar(2020), 31.januar(2020))
        periode.fraOgMed() shouldBe 1.januar(2020)
        periode.tilOgMed() shouldBe 31.januar(2020)
    }

    @Test
    fun `periodisert fra og med og til og med`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val periodisert = periode.tilMånedsperioder()
        periode.fraOgMed() shouldBe 1.januar(2020)
        periode.tilOgMed() shouldBe 31.desember(2020)
        periodisert.first().fraOgMed() shouldBe 1.januar(2020)
        periodisert.first().tilOgMed() shouldBe 31.januar(2020)
        periodisert.last().fraOgMed() shouldBe 1.desember(2020)
        periodisert.last().tilOgMed() shouldBe 31.desember(2020)
    }

    @Test
    fun `periodiserer måneder`() {
        val periode = Periode(1.januar(2020), 31.januar(2020))
        val periodisert = periode.tilMånedsperioder()
        periodisert shouldBe listOf(Periode(1.januar(2020), 31.januar(2020)))
    }

    @Test
    fun `periodiserer flere måneder`() {
        val periode = Periode(1.januar(2020), 30.april(2020))
        val periodisert = periode.tilMånedsperioder()
        periodisert shouldBe listOf(
            Periode(1.januar(2020), 31.januar(2020)),
            Periode(1.februar(2020), 29.februar(2020)),
            Periode(1.mars(2020), 31.mars(2020)),
            Periode(1.april(2020), 30.april(2020))
        )
        periodisert shouldHaveSize periode.antallMåneder()
    }

    @Test
    fun `får ikke opprettet perioder med ugyldige input parametere`() {
        assertThrows<IllegalArgumentException> {
            Periode(10.januar(2002), 31.desember(2020))
        }
        assertThrows<IllegalArgumentException> {
            Periode(1.januar(2002), 10.desember(2020))
        }
        assertThrows<IllegalArgumentException> {
            Periode(10.januar(2002), 1.januar(2020))
        }
    }
}
