package no.nav.su.se.bakover.common.periode

import arrow.core.left
import com.fasterxml.jackson.module.kotlin.readValue
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.objectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal class PeriodeTest {
    @Test
    fun `fra og med og til og med`() {
        val periode = Periode.create(1.januar(2021), 31.januar(2021))
        periode.getFraOgMed() shouldBe 1.januar(2021)
        periode.getTilOgMed() shouldBe 31.januar(2021)
    }

    @Test
    fun `periodisert fra og med og til og med`() {
        val periode = Periode.create(1.januar(2021), 31.desember(2021))
        val periodisert = periode.tilMånedsperioder()
        periode.getFraOgMed() shouldBe 1.januar(2021)
        periode.getTilOgMed() shouldBe 31.desember(2021)
        periodisert.first().getFraOgMed() shouldBe 1.januar(2021)
        periodisert.first().getTilOgMed() shouldBe 31.januar(2021)
        periodisert.last().getFraOgMed() shouldBe 1.desember(2021)
        periodisert.last().getTilOgMed() shouldBe 31.desember(2021)
    }

    @Test
    fun `periodiserer måneder`() {
        val periode = Periode.create(1.januar(2021), 31.januar(2021))
        val periodisert = periode.tilMånedsperioder()
        periodisert shouldBe listOf(Periode.create(1.januar(2021), 31.januar(2021)))
    }

    @Test
    fun `periodiserer flere måneder`() {
        val periode = Periode.create(1.januar(2021), 30.april(2021))
        val periodisert = periode.tilMånedsperioder()
        periodisert shouldBe listOf(
            Periode.create(1.januar(2021), 31.januar(2021)),
            Periode.create(1.februar(2021), 28.februar(2021)),
            Periode.create(1.mars(2021), 31.mars(2021)),
            Periode.create(1.april(2021), 30.april(2021))
        )
        periodisert shouldHaveSize periode.getAntallMåneder()
    }

    @Test
    fun `får ikke opprettet perioder med ugyldige input parametere `() {
        assertThrows<IllegalArgumentException> { Periode.create(10.januar(2021), 31.desember(2021)) }
        assertThrows<IllegalArgumentException> { Periode.create(1.januar(2021), 10.desember(2021)) }
        assertThrows<IllegalArgumentException> { Periode.create(1.februar(2021), 31.januar(2021)) }
    }

    @Test
    fun `får ikke opprettet perioder med ugyldige input parametere trycreate`() {
        Periode.tryCreate(10.januar(2021), 31.desember(2021)) shouldBe Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden.left()
        Periode.tryCreate(1.februar(2021), 31.januar(2021)) shouldBe Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørTilOgMedDato.left()
        Periode.tryCreate(1.januar(2021), 30.desember(2021)) shouldBe Periode.UgyldigPeriode.TilOgMedDatoMåVæreSisteDagIMåneden.left()
    }

    @Test
    fun `periode inneholder en annen periode`() {
        Periode.create(1.januar(2021), 31.desember(2021)) inneholder Periode.create(1.januar(2021), 31.januar(2021)) shouldBe true
        Periode.create(1.januar(2021), 31.desember(2021)) inneholder Periode.create(1.januar(2021), 31.desember(2021)) shouldBe true
        Periode.create(1.januar(2021), 31.desember(2021)) inneholder Periode.create(1.desember(2021), 31.desember(2021)) shouldBe true
        Periode.create(1.januar(2021), 31.desember(2021)) inneholder Periode.create(1.juli(2021), 31.august(2021)) shouldBe true
        Periode.create(1.januar(2022), 31.desember(2022)) inneholder Periode.create(1.juli(2021), 31.august(2021)) shouldBe false
        Periode.create(1.januar(2021), 31.desember(2021)) inneholder Periode.create(1.juli(2022), 31.august(2022)) shouldBe false
    }

    @Test
    fun `periode kan ikke være mer enn 12 måneder`() {
        shouldThrowExactly<IllegalArgumentException> { Periode.create(1.januar(2021), 1.januar(2022)) }
    }

    @Test
    fun `periode kan være 12 måneder`() {
        Periode.create(1.januar(2021), 31.desember(2021))
    }

    @Test
    fun `periode kan være mindre enn 12 måneder`() {
        (1..12).forEach {
            Periode.create(1.januar(2021), LocalDate.of(2021, it, 1).with(TemporalAdjusters.lastDayOfMonth()))
        }
    }

    @Test
    fun `tilstøtende perioder`() {
        Periode.create(1.januar(2021), 31.desember(2021)) tilstøter Periode.create(1.januar(2021), 31.januar(2021)) shouldBe false
        Periode.create(1.januar(2021), 31.desember(2021)) tilstøter Periode.create(1.januar(2022), 31.desember(2022)) shouldBe true
        Periode.create(1.januar(2021), 31.desember(2021)) tilstøter Periode.create(1.januar(2022), 31.desember(2022)) shouldBe true
        Periode.create(1.januar(2021), 31.desember(2021)) tilstøter Periode.create(1.januar(2050), 31.desember(2050)) shouldBe false
        Periode.create(1.januar(2025), 31.desember(2025)) tilstøter Periode.create(1.januar(2024), 30.november(2024)) shouldBe false
        Periode.create(1.januar(2021), 31.mars(2021)) tilstøter Periode.create(1.mai(2021), 30.november(2021)) shouldBe false
        Periode.create(1.januar(2021), 31.mars(2021)) tilstøter Periode.create(1.april(2021), 30.november(2021)) shouldBe true
    }

    @Test
    fun `serialisering av periode`() {
        val expectedJson = """
            {
                "fraOgMed":"2021-01-01",
                "tilOgMed":"2021-12-31"
            }
        """.trimIndent()

        val serialized = objectMapper.writeValueAsString(Periode.create(1.januar(2021), 31.desember(2021)))

        JSONAssert.assertEquals(expectedJson, serialized, true)
    }

    @Test
    fun `deserialisering av periode`() {
        val serialized = """
            {
                "fraOgMed":"2021-01-01",
                "tilOgMed":"2021-12-31"
            }
        """.trimIndent()

        val deserialized = objectMapper.readValue<Periode>(serialized)

        deserialized shouldBe Periode.create(1.januar(2021), 31.desember(2021))
    }
}
