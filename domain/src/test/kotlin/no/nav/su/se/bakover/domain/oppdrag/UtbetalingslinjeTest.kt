package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

internal class UtbetalingslinjeTest {
    @Test
    fun `require fom before tom`() {
        assertThrows<IllegalArgumentException> {
            createUtbetalingslinje(1.januar(2020), 1.januar(2020))
        }
        assertThrows<IllegalArgumentException> {
            createUtbetalingslinje(2.januar(2020), 1.januar(2020))
        }
        createUtbetalingslinje(1.januar(2020), 31.januar(2020)).let {
            it.fraOgMed shouldBe 1.januar(2020)
            it.tilOgMed shouldBe 31.januar(2020)
        }
    }

    private fun createUtbetalingslinje(fraOgMed: LocalDate, tilOgMed: LocalDate) = Utbetalingslinje.Ny(
        opprettet = fixedTidspunkt,
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
        forrigeUtbetalingslinjeId = null,
        beløp = 1000,
        uføregrad = Uføregrad.parse(50),
    )
}
