package no.nav.su.se.bakover.common

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Year

internal class YearRangeTest {

    @Test
    fun `four years`() {
        (Year.of(2020)..Year.of(2023)).size shouldBe 4
    }

    @Test
    fun `start må være før eller lik end`() {
        assertThrows<IllegalArgumentException> { YearRange(Year.of(2023), Year.of(2022)) }
    }

    @Test
    fun `incrementsByOne() gir true dersom listen er sammenhengende`() {
        listOf(Year.of(2021), Year.of(2021), Year.of(2021)).isIncrementingByOne() shouldBe false
        listOf(Year.of(2021), Year.of(2023), Year.of(2025)).isIncrementingByOne() shouldBe false
        listOf(Year.of(2021), Year.of(2022), Year.of(2023)).isIncrementingByOne() shouldBe true
    }

    @Test
    fun `en liste med Year, må være sammenhengende hvis det gjøres en toYearRange`() {
        assertThrows<IllegalArgumentException> {
            listOf(Year.of(2021), Year.of(2021), Year.of(2021)).toYearRange()
            listOf(Year.of(2021), Year.of(2023), Year.of(2025)).toYearRange()
        }

        listOf(Year.of(2021), Year.of(2022), Year.of(2023)).toYearRange() shouldBe
            YearRange(Year.of(2021), Year.of(2023))
    }

    @Nested
    inner class Min {

        @Test
        fun `starter før og ender før`() {
            min(YearRange(2020, 2022), YearRange(2021, 2023)) shouldBe YearRange(2020, 2022)
        }

        @Test
        fun `starter før ender samtidig`() {
            min(YearRange(2020, 2023), YearRange(2021, 2023)) shouldBe YearRange(2020, 2023)
        }

        @Test
        fun `starter før ender etter`() {
            min(YearRange(2021, 2023), YearRange(2020, 2024)) shouldBe YearRange(2020, 2024)
        }

        @Test
        fun `starter samtidig ender før`() {
            min(YearRange(2021, 2022), YearRange(2021, 2023)) shouldBe YearRange(2021, 2022)
        }

        @Test
        fun `starter samtidig ender samtidig`() {
            min(YearRange(2021, 2023), YearRange(2021, 2023)) shouldBe YearRange(2021, 2023)
        }

        @Test
        fun `starter samtidig ender etter`() {
            min(YearRange(2021, 2023), YearRange(2021, 2024)) shouldBe YearRange(2021, 2023)
        }

        @Test
        fun `starter etter ender før`() {
            min(YearRange(2022, 2023), YearRange(2021, 2024)) shouldBe YearRange(2021, 2024)
        }

        @Test
        fun `starter etter ender samtidig`() {
            min(YearRange(2022, 2024), YearRange(2021, 2024)) shouldBe YearRange(2021, 2024)
        }

        @Test
        fun `starter etter ender etter`() {
            min(YearRange(2022, 2024), YearRange(2021, 2023)) shouldBe YearRange(2021, 2023)
        }
    }
}
