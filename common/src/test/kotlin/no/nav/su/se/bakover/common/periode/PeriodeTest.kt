package no.nav.su.se.bakover.common.periode

import arrow.core.left
import arrow.core.right
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
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.test.månedsperiodeApril2021
import no.nav.su.se.bakover.test.månedsperiodeAugust2021
import no.nav.su.se.bakover.test.månedsperiodeDesember2021
import no.nav.su.se.bakover.test.månedsperiodeFebruar2021
import no.nav.su.se.bakover.test.månedsperiodeJanuar2021
import no.nav.su.se.bakover.test.månedsperiodeJuli2021
import no.nav.su.se.bakover.test.månedsperiodeJuni2021
import no.nav.su.se.bakover.test.månedsperiodeMai2021
import no.nav.su.se.bakover.test.månedsperiodeMars2021
import no.nav.su.se.bakover.test.månedsperiodeNovember2021
import no.nav.su.se.bakover.test.månedsperiodeOktober2021
import no.nav.su.se.bakover.test.månedsperiodeSeptember2021
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.skyscreamer.jsonassert.JSONAssert
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

internal class PeriodeTest {
    @Test
    fun `fra og med og til og med`() {
        val periode = månedsperiodeJanuar2021
        periode.fraOgMed shouldBe 1.januar(2021)
        periode.tilOgMed shouldBe 31.januar(2021)
    }

    @Test
    fun `periodisert fra og med og til og med`() {
        val periode = Periode.create(1.januar(2021), 31.desember(2021))
        val periodisert = periode.tilMånedsperioder()
        periode.fraOgMed shouldBe 1.januar(2021)
        periode.tilOgMed shouldBe 31.desember(2021)
        periodisert.first().fraOgMed shouldBe 1.januar(2021)
        periodisert.first().tilOgMed shouldBe 31.januar(2021)
        periodisert.last().fraOgMed shouldBe 1.desember(2021)
        periodisert.last().tilOgMed shouldBe 31.desember(2021)
    }

    @Test
    fun `periodiserer måneder`() {
        val periode = månedsperiodeJanuar2021
        val periodisert = periode.tilMånedsperioder()
        periodisert shouldBe listOf(månedsperiodeJanuar2021)
    }

    @Test
    fun `periodiserer flere måneder`() {
        val periode = Periode.create(1.januar(2021), 30.april(2021))
        val periodisert = periode.tilMånedsperioder()
        periodisert shouldBe listOf(
            månedsperiodeJanuar2021,
            månedsperiodeFebruar2021,
            månedsperiodeMars2021,
            månedsperiodeApril2021,
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
        Periode.tryCreate(
            10.januar(2021),
            31.desember(2021),
        ) shouldBe Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørsteDagIMåneden.left()
        Periode.tryCreate(
            1.februar(2021),
            31.januar(2021),
        ) shouldBe Periode.UgyldigPeriode.FraOgMedDatoMåVæreFørTilOgMedDato.left()
        Periode.tryCreate(
            1.januar(2021),
            30.desember(2021),
        ) shouldBe Periode.UgyldigPeriode.TilOgMedDatoMåVæreSisteDagIMåneden.left()
    }

    @Test
    fun `periode inneholder en annen periode`() {
        Periode.create(1.januar(2021), 31.desember(2021)) inneholder Periode.create(
            1.januar(2021),
            31.januar(2021),
        ) shouldBe true
        Periode.create(1.januar(2021), 31.desember(2021)) inneholder Periode.create(
            1.januar(2021),
            31.desember(2021),
        ) shouldBe true
        Periode.create(1.januar(2021), 31.desember(2021)) inneholder Periode.create(
            1.desember(2021),
            31.desember(2021),
        ) shouldBe true
        Periode.create(1.januar(2021), 31.desember(2021)) inneholder Periode.create(
            1.juli(2021),
            31.august(2021),
        ) shouldBe true
        Periode.create(1.januar(2022), 31.desember(2022)) inneholder Periode.create(
            1.juli(2021),
            31.august(2021),
        ) shouldBe false
        Periode.create(1.januar(2021), 31.desember(2021)) inneholder Periode.create(
            1.juli(2022),
            31.august(2022),
        ) shouldBe false
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
        Periode.create(1.januar(2021), 31.desember(2021)) tilstøter Periode.create(
            1.januar(2021),
            31.januar(2021),
        ) shouldBe false
        Periode.create(1.januar(2021), 31.desember(2021)) tilstøter Periode.create(
            1.januar(2022),
            31.desember(2022),
        ) shouldBe true
        Periode.create(1.januar(2021), 31.desember(2021)) tilstøter Periode.create(
            1.januar(2022),
            31.desember(2022),
        ) shouldBe true
        Periode.create(1.januar(2021), 31.desember(2021)) tilstøter Periode.create(
            1.januar(2050),
            31.desember(2050),
        ) shouldBe false
        Periode.create(1.januar(2025), 31.desember(2025)) tilstøter Periode.create(
            1.januar(2024),
            30.november(2024),
        ) shouldBe false
        Periode.create(1.januar(2021), 31.mars(2021)) tilstøter Periode.create(
            1.mai(2021),
            30.november(2021),
        ) shouldBe false
        Periode.create(1.januar(2021), 31.mars(2021)) tilstøter Periode.create(
            1.april(2021),
            30.november(2021),
        ) shouldBe true
        januar(2021) tilstøter januar(2021) shouldBe true
    }

    @Test
    fun `overlapper enkelt periode`() {
        Periode.create(1.januar(2021), 31.desember(2021)) overlapper
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.januar(2021), 31.desember(2021)) overlapper
            Periode.create(1.juli(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.juli(2021), 31.desember(2021)) overlapper
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.januar(2021), 31.desember(2021)) overlapper
            Periode.create(1.juli(2021), 30.november(2021)) shouldBe true

        Periode.create(1.juli(2021), 30.november(2021)) overlapper
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.januar(2021), 31.desember(2021)) overlapper
            Periode.create(1.desember(2020), 31.januar(2021)) shouldBe true

        Periode.create(1.desember(2020), 31.januar(2021)) overlapper
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.januar(2021), 31.desember(2021)) overlapper
            Periode.create(1.desember(2021), 31.januar(2022)) shouldBe true

        Periode.create(1.desember(2021), 31.januar(2022)) overlapper
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.januar(2021), 31.desember(2021)) overlapper
            Periode.create(1.januar(2020), 31.desember(2022)) shouldBe true

        Periode.create(1.januar(2021), 31.desember(2021)) overlapper
            Periode.create(1.januar(2020), 31.desember(2022)) shouldBe true

        Periode.create(1.januar(2021), 31.desember(2021)) overlapper
            Periode.create(1.januar(2020), 31.desember(2020)) shouldBe false

        Periode.create(1.januar(2020), 31.desember(2020)) overlapper
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe false
    }

    @Test
    fun `overlapper fler perioder`() {

        Periode.create(1.januar(2021), 31.desember(2021)) fullstendigOverlapp
            listOf(Periode.create(1.januar(2021), 31.desember(2021))) shouldBe true

        Periode.create(1.januar(2021), 31.desember(2021)) fullstendigOverlapp
            listOf(
                månedsperiodeJanuar2021,
                månedsperiodeFebruar2021,
                månedsperiodeMars2021,
                månedsperiodeApril2021,
                månedsperiodeMai2021,
                månedsperiodeJuni2021,
                månedsperiodeJuli2021,
                månedsperiodeAugust2021,
                månedsperiodeSeptember2021,
                månedsperiodeOktober2021,
                månedsperiodeNovember2021,
                månedsperiodeDesember2021,
            ) shouldBe true

        Periode.create(1.januar(2021), 31.desember(2021)) fullstendigOverlapp
            listOf(
                Periode.create(1.januar(2021), 30.juni(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
            ) shouldBe true

        Periode.create(1.januar(2021), 31.desember(2021)) fullstendigOverlapp
            listOf(
                Periode.create(1.januar(2021), 30.juni(2021)),
                Periode.create(1.januar(2021), 30.juni(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
            ) shouldBe true

        Periode.create(1.januar(2021), 31.desember(2021)) fullstendigOverlapp
            listOf(
                Periode.create(1.februar(2021), 30.juni(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
            ) shouldBe false

        Periode.create(1.januar(2021), 31.desember(2021)) fullstendigOverlapp
            listOf(
                Periode.create(1.januar(2021), 30.juni(2021)),
                Periode.create(1.juli(2021), 30.november(2021)),
            ) shouldBe false

        Periode.create(1.januar(2021), 31.desember(2021)) fullstendigOverlapp
            listOf(
                Periode.create(1.januar(2021), 31.mai(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
            ) shouldBe false

        Periode.create(1.februar(2021), 31.desember(2021)) fullstendigOverlapp
            listOf(
                Periode.create(1.januar(2021), 30.juni(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
            ) shouldBe false

        Periode.create(1.januar(2021), 30.november(2021)) fullstendigOverlapp
            listOf(
                Periode.create(1.januar(2021), 30.juni(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
            ) shouldBe false

        Periode.create(1.januar(2021), 30.november(2021)) fullstendigOverlapp
            listOf(
                Periode.create(1.januar(2021), 30.juni(2021)),
                Periode.create(1.januar(2021), 30.juni(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
                Periode.create(1.juli(2021), 31.desember(2021)),
            ) shouldBe false

        Periode.create(1.januar(2021), 31.desember(2021)) fullstendigOverlapp
            listOf(
                Periode.create(1.januar(2021), 31.desember(2021)),
                Periode.create(1.januar(2021), 31.desember(2021)),
                Periode.create(1.januar(2021), 31.desember(2021)),
            ) shouldBe true
    }

    @Test
    fun `bevarer original periode dersom maks inneholder original`() {
        Periode.create(1.januar(2021), 31.desember(2021)) snitt Periode.create(
            1.juli(2021),
            31.juli(2021),
        ) shouldBe månedsperiodeJuli2021
        månedsperiodeJuli2021 snitt Periode.create(
            1.januar(2021),
            31.desember(2021),
        ) shouldBe månedsperiodeJuli2021
    }

    @Test
    fun `justerer original periode dersom original inneholder maks`() {
        månedsperiodeJuli2021 snitt Periode.create(
            1.januar(2021),
            31.desember(2021),
        ) shouldBe månedsperiodeJuli2021
        Periode.create(1.januar(2021), 31.desember(2021)) snitt Periode.create(
            1.juli(2021),
            31.juli(2021),
        ) shouldBe månedsperiodeJuli2021
    }

    @Test
    fun `returnerer ingenting hvis det ikke er overlapp mellom maks og original`() {
        månedsperiodeJuli2021 snitt Periode.create(
            1.desember(2021),
            31.desember(2021),
        ) shouldBe null
        månedsperiodeDesember2021 snitt Periode.create(
            1.juli(2021),
            31.juli(2021),
        ) shouldBe null
    }

    @Test
    fun `justerer fraOgMed hvis original starter før maks`() {
        Periode.create(1.juli(2021), 31.desember(2021)) snitt Periode.create(
            1.januar(2021),
            31.desember(2021),
        ) shouldBe Periode.create(1.juli(2021), 31.desember(2021))
        Periode.create(1.januar(2021), 31.desember(2021)) snitt Periode.create(
            1.juli(2021),
            31.desember(2021),
        ) shouldBe Periode.create(1.juli(2021), 31.desember(2021))
    }

    @Test
    fun `justerer tilOgMed hvis original slutter etter maks`() {
        Periode.create(1.januar(2021), 31.juli(2021)) snitt Periode.create(
            1.januar(2021),
            31.desember(2021),
        ) shouldBe Periode.create(1.januar(2021), 31.juli(2021))
        Periode.create(1.januar(2021), 31.desember(2021)) snitt Periode.create(
            1.januar(2021),
            31.juli(2021),
        ) shouldBe Periode.create(1.januar(2021), 31.juli(2021))
    }

    @Test
    fun `justerer fraOgMed hvis original starter før og slutter før maks`() {
        Periode.create(1.juli(2021), 31.desember(2021)) snitt Periode.create(
            1.januar(2021),
            31.oktober(2021),
        ) shouldBe Periode.create(1.juli(2021), 31.oktober(2021))
        Periode.create(1.januar(2021), 31.oktober(2021)) snitt Periode.create(
            1.juli(2021),
            31.desember(2021),
        ) shouldBe Periode.create(1.juli(2021), 31.oktober(2021))
    }

    @Test
    fun `justerer tilOgMed hvis original starter seneere enn og slutter etter maks`() {
        Periode.create(1.januar(2021), 31.juli(2021)) snitt Periode.create(
            1.mars(2021),
            31.desember(2021),
        ) shouldBe Periode.create(1.mars(2021), 31.juli(2021))
        Periode.create(1.mars(2021), 31.desember(2021)) snitt Periode.create(
            1.januar(2021),
            31.juli(2021),
        ) shouldBe Periode.create(1.mars(2021), 31.juli(2021))
    }

    @Test
    fun `starter samtidig`() {
        Periode.create(1.januar(2021), 31.desember(2021)) starterSamtidig
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.januar(2021), 31.desember(2021)) starterSamtidig
            Periode.create(1.februar(2021), 31.desember(2021)) shouldBe false
    }

    @Test
    fun `starter tidligere`() {
        Periode.create(1.januar(2021), 31.desember(2021)) starterTidligere
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe false

        Periode.create(1.januar(2021), 31.desember(2021)) starterTidligere
            Periode.create(1.februar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.februar(2021), 31.desember(2021)) starterTidligere
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe false
    }

    @Test
    fun `starter etter`() {
        Periode.create(1.januar(2021), 31.desember(2021)) starterEtter
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe false

        Periode.create(1.januar(2021), 31.desember(2021)) starterEtter
            Periode.create(1.februar(2021), 31.desember(2021)) shouldBe false

        Periode.create(1.februar(2021), 31.desember(2021)) starterEtter
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe true
    }

    @Test
    fun `slutter samtidig`() {
        Periode.create(1.januar(2021), 31.desember(2021)) slutterSamtidig
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.januar(2021), 30.november(2021)) slutterSamtidig
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe false
    }

    @Test
    fun `slutter tidligere`() {
        Periode.create(1.januar(2021), 31.desember(2021)) slutterTidligere
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe false

        Periode.create(1.januar(2021), 30.november(2021)) slutterTidligere
            Periode.create(1.februar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.februar(2021), 31.desember(2021)) slutterTidligere
            Periode.create(1.januar(2021), 30.november(2021)) shouldBe false
    }

    @Test
    fun før() {
        Periode.create(1.januar(2021), 31.juli(2021)) før
            månedsperiodeDesember2021 shouldBe true

        Periode.create(1.januar(2021), 30.november(2021)) før
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe false

        månedsperiodeDesember2021 før
            månedsperiodeNovember2021 shouldBe false
    }

    @Test
    fun etter() {
        Periode.create(1.januar(2021), 31.juli(2021)) etter
            månedsperiodeDesember2021 shouldBe false

        Periode.create(1.januar(2021), 30.november(2021)) etter
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe false

        månedsperiodeDesember2021 etter
            månedsperiodeNovember2021 shouldBe true
    }

    @Test
    fun `starter samtidig eller senere`() {
        Periode.create(1.januar(2021), 31.desember(2021)) starterSamtidigEllerSenere
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.februar(2021), 31.desember(2021)) starterSamtidigEllerSenere
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.januar(2021), 31.desember(2021)) starterSamtidigEllerSenere
            månedsperiodeDesember2021 shouldBe false
    }

    @Test
    fun `starter samtidig eller tidligere`() {
        Periode.create(1.januar(2021), 31.desember(2021)) starterSamtidigEllerTidligere
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.februar(2021), 31.desember(2021)) starterSamtidigEllerTidligere
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe false

        Periode.create(1.januar(2021), 31.desember(2021)) starterSamtidigEllerTidligere
            månedsperiodeDesember2021 shouldBe true
    }

    @Test
    fun `slutter samtidig eller tidligere`() {
        Periode.create(1.januar(2021), 31.desember(2021)) slutterSamtidigEllerTidligere
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.februar(2021), 30.november(2021)) slutterSamtidigEllerTidligere
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.januar(2021), 31.desember(2021)) slutterSamtidigEllerTidligere
            månedsperiodeNovember2021 shouldBe false
    }

    @Test
    fun `slutter samtidig eller senere`() {
        Periode.create(1.januar(2021), 31.desember(2021)) slutterSamtidigEllerSenere
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.februar(2021), 30.november(2021)) slutterSamtidigEllerSenere
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe false

        Periode.create(1.januar(2021), 31.desember(2021)) slutterSamtidigEllerSenere
            månedsperiodeNovember2021 shouldBe true
    }

    @Test
    fun `slutter inni`() {
        Periode.create(1.januar(2021), 31.desember(2021)) slutterInni
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.januar(2021), 30.november(2021)) slutterInni
            Periode.create(1.januar(2021), 31.desember(2021)) shouldBe true

        Periode.create(1.januar(2021), 31.desember(2021)) slutterInni
            Periode.create(1.januar(2021), 30.november(2021)) shouldBe false

        Periode.create(1.januar(2021), 31.mai(2021)) slutterInni
            Periode.create(1.juli(2021), 30.november(2021)) shouldBe false
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

    @Test
    fun `slår sammen perioder hvis mulig`() {
        månedsperiodeJanuar2021 slåSammen
            månedsperiodeFebruar2021 shouldBe (
            Periode.create(
                1.januar(2021),
                28.februar(2021),
            )
            ).right()

        Periode.create(1.januar(2021), 31.juli(2021)) slåSammen
            månedsperiodeFebruar2021 shouldBe (
            Periode.create(
                1.januar(2021),
                31.juli(2021),
            )
            ).right()

        Periode.create(1.september(2021), 31.desember(2021)) slåSammen
            Periode.create(1.mars(2021), 31.oktober(2021)) shouldBe (
            Periode.create(
                1.mars(2021),
                31.desember(2021),
            )
            ).right()

        månedsperiodeMars2021 slåSammen
            månedsperiodeApril2021 shouldBe (
            Periode.create(
                1.mars(2021),
                30.april(2021),
            )
            ).right()

        Periode.create(1.juni(2021), 31.desember(2021)) slåSammen
            Periode.create(1.mars(2021), 31.mai(2021)) shouldBe (
            Periode.create(
                1.mars(2021),
                31.desember(2021),
            )
            ).right()

        Periode.create(1.juni(2021), 31.juli(2021)) slåSammen
            Periode.create(1.oktober(2021), 31.desember(2021)) shouldBe Periode.PerioderKanIkkeSlåsSammen.left()
    }

    @Test
    fun `reduserer perioder`() {
        listOf(
            månedsperiodeJanuar2021,
            månedsperiodeFebruar2021,
            månedsperiodeMars2021,
            månedsperiodeApril2021,
        ).minsteAntallSammenhengendePerioder() shouldBe listOf(
            Periode.create(1.januar(2021), 30.april(2021)),
        )

        listOf(
            Periode.create(1.januar(2021), 31.mars(2021)),
            Periode.create(1.februar(2021), 30.april(2021)),
        ).minsteAntallSammenhengendePerioder() shouldBe listOf(
            Periode.create(1.januar(2021), 30.april(2021)),
        )

        listOf(
            Periode.create(1.januar(2021), 31.mars(2021)),
            Periode.create(1.mars(2021), 31.desember(2021)),
        ).minsteAntallSammenhengendePerioder() shouldBe listOf(
            Periode.create(1.januar(2021), 31.desember(2021)),
        )

        listOf(
            Periode.create(1.april(2021), 31.juli(2021)),
            Periode.create(1.februar(2021), 31.mars(2021)),
            Periode.create(1.februar(2021), 31.desember(2021)),
        ).minsteAntallSammenhengendePerioder() shouldBe listOf(
            Periode.create(1.februar(2021), 31.desember(2021)),
        )

        listOf(
            Periode.create(1.april(2021), 31.juli(2021)),
            Periode.create(1.februar(2022), 31.mars(2022)),
        ).minsteAntallSammenhengendePerioder() shouldBe listOf(
            Periode.create(1.april(2021), 31.juli(2021)),
            Periode.create(1.februar(2022), 31.mars(2022)),
        )

        listOf(
            Periode.create(1.april(2021), 31.juli(2021)),
        ).minsteAntallSammenhengendePerioder() shouldBe listOf(
            Periode.create(1.april(2021), 31.juli(2021)),
        )

        emptyList<Periode>().minsteAntallSammenhengendePerioder() shouldBe emptyList()

        listOf(
            Periode.create(1.mai(2022), 31.august(2022)),
            Periode.create(1.januar(2023), 30.april(2023)),
            Periode.create(1.september(2022), 31.desember(2022)),
        ).minsteAntallSammenhengendePerioder() shouldBe listOf(Periode.create(1.mai(2022), 30.april(2023)))
    }

    @Test
    fun `forskyvning av perioder`() {
        januar(2021).forskyv(1) shouldBe februar(2021)
        januar(2021).forskyv(-1) shouldBe desember(2020)
        januar(2021).forskyv(12) shouldBe januar(2022)
        januar(2021).forskyv(-12) shouldBe januar(2020)

        Periode.create(1.januar(2021), 31.desember(2021)).forskyv(1) shouldBe
            Periode.create(1.februar(2021), 31.januar(2022))
        Periode.create(1.januar(2021), 31.desember(2021)).forskyv(-1) shouldBe
            Periode.create(1.desember(2020), 30.november(2021))
        Periode.create(1.januar(2021), 31.desember(2021)).forskyv(12) shouldBe
            Periode.create(1.januar(2022), 31.desember(2022))
        Periode.create(1.januar(2021), 31.desember(2021)).forskyv(-12) shouldBe
            Periode.create(1.januar(2020), 31.desember(2020))
    }

    @Test
    fun `periode inneholder dato`() {
        januar(2021) inneholder 15.januar(2021) shouldBe true
        januar(2021) inneholder 15.desember(2021) shouldBe false
        Periode.create(1.januar(2021), 31.desember(2021)) inneholder 15.januar(2021) shouldBe true
        Periode.create(1.januar(2021), 31.desember(2021)) inneholder 15.desember(2021) shouldBe true
    }

    @Test
    fun `minus`() {
        januar(2021) minus februar(2021) shouldBe listOf(januar(2021))
        januar(2021) minus januar(2021) shouldBe emptyList()
        Periode.create(1.januar(2021), 31.desember(2021)) minus juni(2021) shouldBe listOf(
            Periode.create(1.januar(2021), 31.mai(2021)),
            Periode.create(1.juli(2021), 31.desember(2021)),
        )
        Periode.create(1.januar(2021), 31.desember(2021)) minus
            Periode.create(1.mars(2021), 31.juli(2021)) shouldBe listOf(
            Periode.create(1.januar(2021), 28.februar(2021)),
            Periode.create(1.august(2021), 31.desember(2021)),
        )
    }

    @Test
    fun `minus liste`() {
        listOf(
            januar(2021),
            februar(2021),
            mars(2021),
        ).minus(listOf(januar(2021))) shouldBe listOf(
            Periode.create(1.februar(2021), 31.mars(2021)),
        )

        listOf(
            januar(2021),
            februar(2021),
            mars(2021),
        ).minus(listOf(februar(2021))) shouldBe listOf(
            månedsperiodeJanuar2021,
            månedsperiodeMars2021,
        )

        listOf(
            Periode.create(1.januar(2021), 31.desember(2021)),
            Periode.create(1.mars(2022), 31.mai(2022)),
        ).minus(
            listOf(
                Periode.create(1.april(2021), 31.august(2021)),
                januar(2022),
                februar(2022),
                mars(2022),
            ),
        ) shouldBe listOf(
            Periode.create(1.januar(2021), 31.mars(2021)),
            Periode.create(1.september(2021), 31.desember(2021)),
            Periode.create(1.april(2022), 31.mai(2022)),
        )
    }

    @Test
    fun `inneholder alle`() {
        Periode.create(1.januar(2021), 31.desember(2021)).inneholderAlle(
            listOf(
                januar(2021),
                februar(2021),
                Periode.create(1.mars(2021), 31.desember(2021)),
            ),
        ) shouldBe true

        Periode.create(1.januar(2021), 31.desember(2021)).inneholderAlle(
            listOf(
                januar(2021),
                februar(2021),
                Periode.create(1.mars(2021), 31.desember(2021)),
                januar(2022),
            ),
        ) shouldBe false

        Periode.create(1.januar(2021), 31.desember(2021)).inneholderAlle(
            emptyList(),
        ) shouldBe true

        listOf(
            januar(2021),
        ).inneholderAlle(
            listOf(
                januar(2021),
                januar(2021),
            ),
        ) shouldBe true

        listOf(
            Periode.create(1.januar(2021), 31.desember(2021)),
        ).inneholderAlle(
            listOf(
                Periode.create(1.januar(2021), 31.desember(2021)),
                Periode.create(1.januar(2021), 31.desember(2021)),
            ),
        ) shouldBe true

        listOf(
            Periode.create(1.januar(2021), 31.desember(2021)),
            Periode.create(1.januar(2021), 31.desember(2021)),
        ).inneholderAlle(
            listOf(
                Periode.create(1.januar(2021), 31.desember(2021)),
            ),
        ) shouldBe true

        emptyList<Periode>().inneholderAlle(
            listOf(
                Periode.create(1.januar(2021), 31.desember(2021)),
            ),
        ) shouldBe false

        listOf(
            Periode.create(1.mai(2021), 31.januar(2022)),
            Periode.create(1.februar(2022), 30.april(2022)),
        ).inneholderAlle(
            listOf(
                Periode.create(1.mai(2021), 31.desember(2021)),
                Periode.create(1.mars(2022), 30.april(2022)),
            ),
        ) shouldBe true
    }

    @Test
    fun `overlappende`() {
        listOf(
            januar(2021),
            januar(2021),
        ).harOverlappende() shouldBe true

        listOf(
            januar(2021),
            februar(2021),
        ).harOverlappende() shouldBe false

        listOf<Periode>().harOverlappende() shouldBe false

        listOf(
            mai(2021),
            Periode.create(1.januar(2021), 31.desember(2021)),
        ).harOverlappende() shouldBe true

        listOf(
            mai(2021),
            Periode.create(1.januar(2021), 31.desember(2021)),
            Periode.create(1.januar(2022), 31.desember(2022))
        ).harOverlappende() shouldBe true
    }
}
