package no.nav.su.se.bakover.common.periode

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.februar
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.november
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth

internal class MånedsperiodeTest {
    @Nested
    inner class YearMonthConstructor {
        @Test
        fun `fraOgMed`() {
            Månedsperiode(YearMonth.of(2021, 1)).fraOgMed.let {
                it shouldBe LocalDate.of(2021, 1, 1)
                it shouldBe LocalDate.of(2021, Month.JANUARY, 1)
            }

            Månedsperiode(YearMonth.of(2021, Month.JANUARY)).fraOgMed.let {
                it shouldBe LocalDate.of(2021, 1, 1)
                it shouldBe LocalDate.of(2021, Month.JANUARY, 1)
            }
        }

        @Test
        fun `tilOgMed`() {
            Månedsperiode(YearMonth.of(2021, Month.JANUARY)).tilOgMed shouldBe LocalDate.of(2021, Month.JANUARY, 31)
        }

        @Test
        fun `måned`() {
            Månedsperiode(YearMonth.of(2021, Month.JANUARY)).måned shouldBe YearMonth.of(2021, Month.JANUARY)
        }

        @Test
        fun `toMånedsperiode`() {
            Månedsperiode(YearMonth.of(2021, Month.JANUARY)).toMånedsperiode() shouldBe Månedsperiode(
                YearMonth.of(
                    2021,
                    Month.JANUARY,
                ),
            )
        }

        @Test
        fun `rangeTo`() {
            Månedsperiode(
                YearMonth.of(2021, Month.JANUARY),
            )..Månedsperiode(
                YearMonth.of(2021, Month.JANUARY),
            ) shouldBe Månedsperiode(YearMonth.of(2021, Month.JANUARY))

            Månedsperiode(
                YearMonth.of(2021, Month.JANUARY),
            )..Månedsperiode(
                YearMonth.of(2021, Month.FEBRUARY),
            ) shouldBe Periode.create(1.januar(2021), 28.februar(2021))

            Månedsperiode(
                YearMonth.of(2021, Month.JANUARY),
            )..Månedsperiode(
                YearMonth.of(2021, Month.MARCH),
            ) shouldBe Periode.create(1.januar(2021), 31.mars(2021))

            Månedsperiode(
                YearMonth.of(2021, Month.NOVEMBER),
            )..Månedsperiode(
                YearMonth.of(2022, Month.MARCH),
            ) shouldBe Periode.create(1.november(2021), 31.mars(2022))
        }
    }

    @Nested
    inner class FraOgMedTilOgMedConstructor {
        @Test
        fun `januar`() {
            Månedsperiode(
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.januar(2021),
            ) shouldBe Månedsperiode(YearMonth.of(2021, Month.JANUARY))
        }

        @Test
        fun `fra og med må være første i måneden`() {
            shouldThrow<IllegalArgumentException> {
                Månedsperiode(
                    fraOgMed = 2.januar(2021),
                    tilOgMed = 31.januar(2021),
                )
            }.message shouldBe "FraOgMedDatoMåVæreFørsteDagIMåneden"
        }

        @Test
        fun `til og med må være siste i måneden`() {
            shouldThrow<IllegalArgumentException> {
                Månedsperiode(
                    fraOgMed = 1.januar(2021),
                    tilOgMed = 1.januar(2021),
                )
            }.message shouldBe "TilOgMedDatoMåVæreSisteDagIMåneden"
        }

        @Test
        fun `fraOgMed og tilOgMed må være innenfor samme måned`() {
            shouldThrow<IllegalArgumentException> {
                Månedsperiode(
                    fraOgMed = 1.februar(2021),
                    tilOgMed = 31.januar(2021),
                )
            }.message shouldBe "fraOgMed og tilOgMed må være innenfor samme måned"
        }

        @Test
        fun `fraOgMed og tilOgMed må være innenfor samme år`() {
            shouldThrow<IllegalArgumentException> {
                Månedsperiode(
                    fraOgMed = 1.desember(2021),
                    tilOgMed = 31.januar(2022),
                )
            }.message shouldBe "fraOgMed og tilOgMed må være innenfor samme år"
        }
    }
}
