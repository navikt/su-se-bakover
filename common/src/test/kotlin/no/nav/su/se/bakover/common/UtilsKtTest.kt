package no.nav.su.se.bakover.common

import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.temporal.ChronoUnit

internal class UtilsKtTest {

    @Test
    fun `convenient dates`() {
        1.januar(2010) shouldBe LocalDate.of(2010, Month.JANUARY, 1)
        2.februar(2011) shouldBe LocalDate.of(2011, Month.FEBRUARY, 2)
        3.mars(2012) shouldBe LocalDate.of(2012, Month.MARCH, 3)
        4.april(2013) shouldBe LocalDate.of(2013, Month.APRIL, 4)
        5.mai(2014) shouldBe LocalDate.of(2014, Month.MAY, 5)
        6.juni(2015) shouldBe LocalDate.of(2015, Month.JUNE, 6)
        7.juli(2016) shouldBe LocalDate.of(2016, Month.JULY, 7)
        8.august(2017) shouldBe LocalDate.of(2017, Month.AUGUST, 8)
        9.september(2018) shouldBe LocalDate.of(2018, Month.SEPTEMBER, 9)
        10.oktober(2019) shouldBe LocalDate.of(2019, Month.OCTOBER, 10)
        11.november(2020) shouldBe LocalDate.of(2020, Month.NOVEMBER, 11)
        12.desember(2021) shouldBe LocalDate.of(2021, Month.DECEMBER, 12)

        assertThrows<DateTimeException> {
            51.juni(2020)
        }
    }

    @Test
    fun `truncate instant to same format as repo, precision in millis`() {
        val instant = Instant.now()
        val truncated = instant.truncatedTo(ChronoUnit.MILLIS)
        instant.toEpochMilli() - truncated.toEpochMilli() shouldBe 0
        instant.nano - truncated.nano shouldBeGreaterThan 0

        Instant.now().toString() shouldHaveLength 27
        now().toString() shouldHaveLength 24
    }

    @Test
    fun `start and end of day`() {
        1.januar(2020).startOfDay().toString() shouldBe "2020-01-01T00:00:00Z"
        1.januar(2020).endOfDay().toString() shouldBe "2020-01-01T23:59:59.999999999Z"
    }

    @Test
    fun `instants between others`() {
        val sept5 = 5.september(2020).startOfDay()
        sept5.between(
            start = 5.september(2020).startOfDay(),
            end = 5.september(2020).endOfDay()
        ) shouldBe true

        sept5.between(
            start = 4.september(2020).startOfDay(),
            end = 5.september(2020).startOfDay()
        ) shouldBe true

        sept5.between(
            start = 1.september(2020).startOfDay(),
            end = 10.september(2020).startOfDay()
        ) shouldBe true

        sept5.between(
            start = 1.januar(2020).startOfDay(),
            end = 10.januar(2020).startOfDay()
        ) shouldBe false

        sept5.between(
            start = 1.desember(2020).startOfDay(),
            end = 10.desember(2020).startOfDay()
        ) shouldBe false
    }
}
