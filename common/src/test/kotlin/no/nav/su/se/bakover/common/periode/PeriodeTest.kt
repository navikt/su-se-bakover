package no.nav.su.se.bakover.common.periode

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mars
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class PeriodeTest {
    @Test
    fun `fra og med og til og med`() {
        val periode = Periode(1.januar(2020), 31.januar(2020))
        periode.getFraOgMed() shouldBe 1.januar(2020)
        periode.getTilOgMed() shouldBe 31.januar(2020)
    }

    @Test
    fun `periodisert fra og med og til og med`() {
        val periode = Periode(1.januar(2020), 31.desember(2020))
        val periodisert = periode.tilMånedsperioder()
        periode.getFraOgMed() shouldBe 1.januar(2020)
        periode.getTilOgMed() shouldBe 31.desember(2020)
        periodisert.first().getFraOgMed() shouldBe 1.januar(2020)
        periodisert.first().getTilOgMed() shouldBe 31.januar(2020)
        periodisert.last().getFraOgMed() shouldBe 1.desember(2020)
        periodisert.last().getTilOgMed() shouldBe 31.desember(2020)
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
        periodisert shouldHaveSize periode.getAntallMåneder()
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

    @Test
    fun `periode inneholder en annen periode`() {
        Periode(1.januar(2021), 31.desember(2021)) inneholder Periode(1.januar(2021), 31.januar(2021)) shouldBe true
        Periode(1.januar(2021), 31.desember(2021)) inneholder Periode(1.januar(2021), 31.desember(2021)) shouldBe true
        Periode(1.januar(2021), 31.desember(2021)) inneholder Periode(1.desember(2021), 31.desember(2021)) shouldBe true
        Periode(1.januar(2021), 31.desember(2021)) inneholder Periode(1.januar(2020), 31.desember(2021)) shouldBe false
        Periode(1.januar(2021), 31.desember(2021)) inneholder Periode(1.januar(2021), 31.desember(2022)) shouldBe false
        Periode(1.januar(2021), 31.desember(2021)) inneholder Periode(1.juli(2021), 31.august(2021)) shouldBe true
        Periode(1.januar(2021), 31.desember(2021)) inneholder Periode(1.juli(2019), 31.august(2019)) shouldBe false
        Periode(1.januar(2021), 31.desember(2021)) inneholder Periode(1.juli(2022), 31.august(2022)) shouldBe false
    }

    @Test
    fun `tilstøtende perioder`() {
        Periode(1.januar(2021), 31.desember(2021)) tilstøter Periode(1.januar(2021), 31.januar(2021)) shouldBe false
        Periode(1.januar(2021), 31.desember(2021)) tilstøter Periode(1.januar(2022), 31.desember(2022)) shouldBe true
        Periode(1.januar(2021), 31.desember(2021)) tilstøter Periode(1.januar(2020), 31.desember(2020)) shouldBe true
        Periode(1.januar(2021), 31.desember(2021)) tilstøter Periode(1.januar(2050), 31.desember(2050)) shouldBe false
        Periode(1.januar(2021), 31.desember(2021)) tilstøter Periode(1.januar(2015), 31.desember(2015)) shouldBe false
    }
}
