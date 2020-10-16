package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.Fnr
import org.junit.jupiter.api.Test

internal class UtbetalingTest {

    private val fnr = Fnr("12345678910")

    @Test
    fun `tidligste og seneste dato`() {
        createUtbetaling().tidligsteDato() shouldBe 1.januar(2019)
        createUtbetaling().senesteDato() shouldBe 31.januar(2021)
    }

    @Test
    fun `brutto beløp`() {
        createUtbetaling(utbetalingsLinjer = emptyList()).bruttoBeløp() shouldBe 0
        createUtbetaling().bruttoBeløp() shouldBe 1500
    }

    private fun createUtbetaling(
        opprettet: Tidspunkt = Tidspunkt.now(),
        utbetalingsLinjer: List<Utbetalingslinje> = createUtbetalingslinjer()
    ) = Utbetaling.UtbetalingForSimulering(
        utbetalingslinjer = utbetalingsLinjer,
        fnr = fnr,
        opprettet = opprettet,
        type = Utbetaling.UtbetalingsType.NY
    )

    private fun createUtbetalingslinjer() = listOf(
        Utbetalingslinje(
            fraOgMed = 1.januar(2019),
            tilOgMed = 30.april(2020),
            beløp = 500,
            forrigeUtbetalingslinjeId = null
        ),
        Utbetalingslinje(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.august(2020),
            beløp = 500,
            forrigeUtbetalingslinjeId = null
        ),
        Utbetalingslinje(
            fraOgMed = 1.september(2020),
            tilOgMed = 31.januar(2021),
            beløp = 500,
            forrigeUtbetalingslinjeId = null
        )
    )
}
