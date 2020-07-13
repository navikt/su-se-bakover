package no.nav.su.se.bakover.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month.APRIL
import java.time.Month.JANUARY
import java.time.Month.MAY

internal class GrunnbeløpTest {
    @Test
    fun høy2017Fradato2018() {
        Grunnbeløp.`2,48G`.fraDato(LocalDate.of(2018, JANUARY, 1)) shouldBe 2.48 * 93634
    }

    @Test
    fun lav2017Fradato2018() {
        Grunnbeløp.`2,28G`.fraDato(LocalDate.of(2018, JANUARY, 1)) shouldBe 2.28 * 93634
    }

    @Test
    fun høy2017Fradato30April2018() {
        Grunnbeløp.`2,48G`.fraDato(LocalDate.of(2018, APRIL, 30)) shouldBe 2.48 * 93634
    }

    @Test
    fun lav2017Fradato30April2018() {
        Grunnbeløp.`2,28G`.fraDato(LocalDate.of(2018, APRIL, 30)) shouldBe 2.28 * 93634
    }

    @Test
    fun høy2018Fradato2018() {
        Grunnbeløp.`2,48G`.fraDato(LocalDate.of(2018, MAY, 1)) shouldBe 2.48 * 96883
    }

    @Test
    fun lav2018Fradato2018() {
        Grunnbeløp.`2,28G`.fraDato(LocalDate.of(2018, MAY, 1)) shouldBe 2.28 * 96883
    }

    @Test
    fun høy2019Fradato2025() {
        Grunnbeløp.`2,48G`.fraDato(LocalDate.of(2025, MAY, 1)) shouldBe 2.48 * 99858
    }

    @Test
    fun lav2019Fradato2025() {
        Grunnbeløp.`2,28G`.fraDato(LocalDate.of(2025, MAY, 1)) shouldBe 2.28 * 99858
    }
}
