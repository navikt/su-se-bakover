package no.nav.su.se.bakover.common.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.august
import no.nav.su.se.bakover.common.extensions.between
import no.nav.su.se.bakover.common.extensions.ddMMyyyy
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.endOfDay
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.isEqualOrBefore
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.extensions.juni
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.extensions.november
import no.nav.su.se.bakover.common.extensions.oktober
import no.nav.su.se.bakover.common.extensions.september
import no.nav.su.se.bakover.common.extensions.startOfDay
import no.nav.su.se.bakover.common.extensions.toBrevformat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.DateTimeException
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset

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
    fun `start and end of day`() {
        1.januar(2020).startOfDay(ZoneOffset.UTC).toString() shouldBe "2020-01-01T00:00:00Z"
        1.januar(2020).endOfDay(ZoneOffset.UTC).toString() shouldBe "2020-01-01T23:59:59.999999Z"
        // Oslo default
        1.januar(2020).startOfDay().toString() shouldBe "2019-12-31T23:00:00Z"
        1.januar(2020).endOfDay().toString() shouldBe "2020-01-01T22:59:59.999999Z"
    }

    @Test
    fun `instants between others`() {
        val sept5 = 5.september(2020).startOfDay()
        sept5.between(
            fraOgMed = 5.september(2020).startOfDay(),
            tilOgMed = 5.september(2020).endOfDay(),
        ) shouldBe true

        sept5.between(
            fraOgMed = 4.september(2020).startOfDay(),
            tilOgMed = 5.september(2020).startOfDay(),
        ) shouldBe true

        sept5.between(
            fraOgMed = 1.september(2020).startOfDay(),
            tilOgMed = 10.september(2020).startOfDay(),
        ) shouldBe true

        sept5.between(
            fraOgMed = 1.januar(2020).startOfDay(),
            tilOgMed = 10.januar(2020).startOfDay(),
        ) shouldBe false

        sept5.between(
            fraOgMed = 1.desember(2020).startOfDay(),
            tilOgMed = 10.desember(2020).startOfDay(),
        ) shouldBe false
    }

    @Test
    fun `Formatterer dato til format ddMMyyyy`() {
        1.januar(2020).ddMMyyyy() shouldBe "01.01.2020"
    }

    @Test
    fun `isEqualOrBefore`() {
        1.januar(2021).isEqualOrBefore(1.januar(2021)) shouldBe true
        1.januar(2021).isEqualOrBefore(2.januar(2021)) shouldBe true
        2.januar(2021).isEqualOrBefore(1.januar(2021)) shouldBe false
    }

    @Test
    fun brevFormat() {
        1.januar(2021).toBrevformat() shouldBe "1. januar 2021"
        2.februar(2021).toBrevformat() shouldBe "2. februar 2021"
        3.mars(2021).toBrevformat() shouldBe "3. mars 2021"
        4.april(2021).toBrevformat() shouldBe "4. april 2021"
        5.mai(2021).toBrevformat() shouldBe "5. mai 2021"
        6.juni(2021).toBrevformat() shouldBe "6. juni 2021"
        7.juli(2021).toBrevformat() shouldBe "7. juli 2021"
        8.august(2021).toBrevformat() shouldBe "8. august 2021"
        9.september(2021).toBrevformat() shouldBe "9. september 2021"
        10.oktober(2021).toBrevformat() shouldBe "10. oktober 2021"
        11.november(2021).toBrevformat() shouldBe "11. november 2021"
        12.desember(2021).toBrevformat() shouldBe "12. desember 2021"

        // Skuddår
        29.februar(2020).toBrevformat() shouldBe "29. februar 2020"
        29.februar(2024).toBrevformat() shouldBe "29. februar 2024"
    }
}
