package no.nav.su.se.bakover.common.domain.periode

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.extensions.november
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.april
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.test.fixedClock
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

internal class MånedTest {

    @Nested
    inner class `until()` {
        @Test
        fun `februar til januar gir tom liste`() {
            februar(2021).until(januar(2021)) shouldBe emptyList()
        }

        @Test
        fun `januar til januar gir tom liste`() {
            januar(2021).until(januar(2021)) shouldBe emptyList()
        }

        @Test
        fun `januar til februar gir januar`() {
            januar(2021).until(februar(2021)) shouldBe listOf(januar(2021))
        }

        @Test
        fun `januar until april gir januar, februar og mars`() {
            januar(2021).until(april(2021)) shouldBe listOf(
                januar(2021),
                februar(2021),
                mars(2021),
            )
        }
    }

    @Nested
    inner class `plusMonths()` {
        @Test
        fun `januar minus 1 gir januar`() {
            januar(2021).plusMonths(-1) shouldBe desember(2020)
        }

        @Test
        fun `januar pluss 0 gir januar`() {
            januar(2021).plusMonths(0) shouldBe januar(2021)
        }

        @Test
        fun `januar pluss 1 gir februar`() {
            januar(2021).plusMonths(1) shouldBe februar(2021)
        }

        @Test
        fun `januar pluss 12 gir neste januar`() {
            januar(2021).plusMonths(12) shouldBe januar(2022)
        }
    }

    @Test
    fun `equals`() {
        januar(2021) shouldBe januar(2021)
        januar(2021) shouldNotBe februar(2021)

        Måned.fra(1.januar(2021), 31.januar(2021)) shouldBe Måned.fra(1.januar(2021), 31.januar(2021))
        Måned.fra(1.januar(2021), 31.januar(2021)) shouldNotBe Måned.fra(1.februar(2021), 28.februar(2021))

        januar(2021) shouldBe Måned.fra(1.januar(2021), 31.januar(2021))
        januar(2021) shouldBe Periode.create(1.januar(2021), 31.januar(2021))

        februar(2021) shouldNotBe Måned.fra(1.januar(2021), 31.januar(2021))
        februar(2021) shouldNotBe Periode.create(1.januar(2021), 31.januar(2021))
    }

    @Test
    fun `hashCodes`() {
        januar(2021).hashCode() shouldBe Periode.create(1.januar(2021), 31.januar(2021)).hashCode()
        februar(2021).hashCode() shouldNotBe Periode.create(1.januar(2021), 31.januar(2021)).hashCode()
    }

    @Test
    fun januar2021() {
        Måned.now(fixedClock) shouldBe januar(2021)
    }

    @Nested
    inner class FraOgMedTilOgMedConstructor {
        @Test
        fun `januar`() {
            Måned.fra(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.januar(2021),
            ) shouldBe Måned.fra(YearMonth.of(2021, Month.JANUARY))
        }

        @Test
        fun `fra og med må være første i måneden`() {
            shouldThrow<IllegalArgumentException> {
                Måned.fra(
                    fraOgMed = 2.januar(2021),
                    tilOgMed = 31.januar(2021),
                )
            }.message shouldBe "fraOgMed: 2021-01-02 må være den 1. i måneden for å mappes til en måned."
        }

        @Test
        fun `til og med må være siste i måneden`() {
            shouldThrow<IllegalArgumentException> {
                Måned.fra(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 1.januar(2021),
                )
            }.message shouldBe "tilOgMed: 2021-01-01 må være den siste i måneden for å mappes til en måned."
        }

        @Test
        fun `fraOgMed og tilOgMed må være innenfor samme måned`() {
            shouldThrow<IllegalArgumentException> {
                Måned.fra(
                    fraOgMed = 1.februar(2021),
                    tilOgMed = 31.januar(2021),
                )
            }.message shouldBe "fraOgMed og tilOgMed må være innenfor samme måned"
        }

        @Test
        fun `fraOgMed og tilOgMed må være innenfor samme år`() {
            shouldThrow<IllegalArgumentException> {
                Måned.fra(
                    fraOgMed = 1.desember(2021),
                    tilOgMed = 31.januar(2022),
                )
            }.message shouldBe "fraOgMed og tilOgMed må være innenfor samme år"
        }
    }

    @Nested
    inner class YearMonthConstructor {
        @Test
        fun `fraOgMed`() {
            Måned.fra(YearMonth.of(2021, 1)).fraOgMed.let {
                it shouldBe LocalDate.of(2021, 1, 1)
                it shouldBe LocalDate.of(2021, Month.JANUARY, 1)
            }

            Måned.fra(YearMonth.of(2021, Month.JANUARY)).fraOgMed.let {
                it shouldBe LocalDate.of(2021, 1, 1)
                it shouldBe LocalDate.of(2021, Month.JANUARY, 1)
            }
        }

        @Test
        fun `tilOgMed`() {
            Måned.fra(YearMonth.of(2021, Month.JANUARY)).tilOgMed shouldBe LocalDate.of(2021, Month.JANUARY, 31)
        }

        @Test
        fun `rangeTo`() {
            Måned.fra(
                YearMonth.of(2021, Month.JANUARY),
            )..Måned.fra(
                YearMonth.of(2021, Month.JANUARY),
            ) shouldBe Måned.fra(YearMonth.of(2021, Month.JANUARY))

            Måned.fra(
                YearMonth.of(2021, Month.JANUARY),
            )..Måned.fra(
                YearMonth.of(2021, Month.FEBRUARY),
            ) shouldBe Periode.create(1.januar(2021), 28.februar(2021))

            Måned.fra(
                YearMonth.of(2021, Month.JANUARY),
            )..Måned.fra(
                YearMonth.of(2021, Month.MARCH),
            ) shouldBe Periode.create(1.januar(2021), 31.mars(2021))

            Måned.fra(
                YearMonth.of(2021, Month.NOVEMBER),
            )..Måned.fra(
                YearMonth.of(2022, Month.MARCH),
            ) shouldBe Periode.create(1.november(2021), 31.mars(2022))
        }
    }

    @Test
    fun `allerede opprettede måneder caches`() {
        Periode.create(1.januar(2022), 31.januar(2022)).måneder().single() shouldBeSameInstanceAs januar(2022)
        Måned.fra(YearMonth.of(2022, Month.JANUARY)) shouldBeSameInstanceAs januar(2022)
        Måned.fra(1.januar(2022), 31.januar(2022)) shouldBeSameInstanceAs Måned.fra(YearMonth.of(2022, Month.JANUARY))
    }
}
