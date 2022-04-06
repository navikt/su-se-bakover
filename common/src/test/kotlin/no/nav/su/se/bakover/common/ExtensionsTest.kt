package no.nav.su.se.bakover.common

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Month
import java.time.YearMonth

internal class ExtensionsTest {
    @Test
    fun `months until`() {
        YearMonth.of(2021, Month.JANUARY).monthsUntil(YearMonth.of(2022, Month.JULY)) shouldBe listOf(
            YearMonth.of(2021, Month.JANUARY),
            YearMonth.of(2021, Month.FEBRUARY),
            YearMonth.of(2021, Month.MARCH),
            YearMonth.of(2021, Month.APRIL),
            YearMonth.of(2021, Month.MAY),
            YearMonth.of(2021, Month.JUNE),
            YearMonth.of(2021, Month.JULY),
            YearMonth.of(2021, Month.AUGUST),
            YearMonth.of(2021, Month.SEPTEMBER),
            YearMonth.of(2021, Month.OCTOBER),
            YearMonth.of(2021, Month.NOVEMBER),
            YearMonth.of(2021, Month.DECEMBER),
            YearMonth.of(2022, Month.JANUARY),
            YearMonth.of(2022, Month.FEBRUARY),
            YearMonth.of(2022, Month.MARCH),
            YearMonth.of(2022, Month.APRIL),
            YearMonth.of(2022, Month.MAY),
            YearMonth.of(2022, Month.JUNE),
        )
    }
}
