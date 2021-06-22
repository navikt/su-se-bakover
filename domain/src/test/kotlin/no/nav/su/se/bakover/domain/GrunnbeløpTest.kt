package no.nav.su.se.bakover.domain

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month.APRIL
import java.time.Month.JANUARY
import java.time.Month.MAY

internal class GrunnbeløpTest {
    @Test
    fun høy2017forJanuar2018() {
        Grunnbeløp.`2,48G`.påDato(LocalDate.of(2018, JANUARY, 1)) shouldBe 2.48 * 93634
    }

    @Test
    fun ordinær2017forJanuar2018() {
        Grunnbeløp.`2,28G`.påDato(LocalDate.of(2018, JANUARY, 1)) shouldBe 2.28 * 93634
    }

    @Test
    fun høy2017for30April2018() {
        Grunnbeløp.`2,48G`.påDato(LocalDate.of(2018, APRIL, 30)) shouldBe 2.48 * 93634
    }

    @Test
    fun ordinær2017for30April2018() {
        Grunnbeløp.`2,28G`.påDato(LocalDate.of(2018, APRIL, 30)) shouldBe 2.28 * 93634
    }

    @Test
    fun høy2018forMai2018() {
        Grunnbeløp.`2,48G`.påDato(LocalDate.of(2018, MAY, 1)) shouldBe 2.48 * 96883
    }

    @Test
    fun ordinær2018forMai2018() {
        Grunnbeløp.`2,28G`.påDato(LocalDate.of(2018, MAY, 1)) shouldBe 2.28 * 96883
    }

    @Test
    fun høy2021for2021() {
        Grunnbeløp.`2,48G`.påDato(LocalDate.of(2021, MAY, 1)) shouldBe 2.48 * 106399
    }

    @Test
    fun ordinær2021for2021() {
        Grunnbeløp.`2,28G`.påDato(LocalDate.of(2021, MAY, 1)) shouldBe 2.28 * 106399
    }

    @Test
    fun `finn siste g-endringsdato for 2020-01-01`() {
        Grunnbeløp.`2,48G`.datoForSisteEndringAvGrunnbeløp(
            LocalDate.of(2020, JANUARY, 1),
        ) shouldBe LocalDate.of(2019, MAY, 1)
    }

    @Test
    fun `finn siste g-endringsdato for 2021-04-30`() {
        Grunnbeløp.`2,48G`.datoForSisteEndringAvGrunnbeløp(
            LocalDate.of(2021, APRIL, 30),
        ) shouldBe LocalDate.of(2020, MAY, 1)
    }

    @Test
    fun `finn siste g-endringsdato for 2021-05-01`() {
        Grunnbeløp.`2,48G`.datoForSisteEndringAvGrunnbeløp(
            LocalDate.of(2021, MAY, 1),
        ) shouldBe LocalDate.of(2021, MAY, 1)
    }

    @Test
    fun `fra 2022-05-01`() {
        Grunnbeløp.`0,5G`.gyldigPåDatoOgSenere(LocalDate.of(2021, 5, 1)) shouldBe listOf(
            LocalDate.of(2021, 5, 1) to 53200,
        )
    }

    @Test
    fun `fra 2021-05-01`() {
        Grunnbeløp.`0,5G`.gyldigPåDatoOgSenere(LocalDate.of(2021, 5, 1)) shouldBe listOf(
            LocalDate.of(2021, 5, 1) to 53200,
        )
    }

    @Test
    fun `fra 2021-04-30`() {
        Grunnbeløp.`0,5G`.gyldigPåDatoOgSenere(LocalDate.of(2021, 4, 30)) shouldBe listOf(
            LocalDate.of(2021, 5, 1) to 53200,
            LocalDate.of(2020, 5, 1) to 50676,
        )
    }

    @Test
    fun `fra 2021-01-01`() {
        Grunnbeløp.`0,5G`.gyldigPåDatoOgSenere(LocalDate.of(2021, 1, 1)) shouldBe listOf(
            LocalDate.of(2021, 5, 1) to 53200,
            LocalDate.of(2020, 5, 1) to 50676,
        )
    }

    @Test
    fun `fra 2020-05-01`() {
        Grunnbeløp.`0,5G`.gyldigPåDatoOgSenere(LocalDate.of(2020, 5, 1)) shouldBe listOf(
            LocalDate.of(2021, 5, 1) to 53200,
            LocalDate.of(2020, 5, 1) to 50676,
        )
    }

    @Test
    fun `fra 2020-04-30`() {
        Grunnbeløp.`0,5G`.gyldigPåDatoOgSenere(LocalDate.of(2020, 4, 30)) shouldBe listOf(
            LocalDate.of(2021, 5, 1) to 53200,
            LocalDate.of(2020, 5, 1) to 50676,
            LocalDate.of(2019, 5, 1) to 49929,
        )
    }
}
