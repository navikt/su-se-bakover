package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import org.junit.jupiter.api.Test
import java.time.LocalDate

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

    @Test
    fun `er førstegangsutbetaling`() {
        createUtbetaling(utbetalingsLinjer = listOf(createUtbetalingslinje(forrigeUtbetalingslinjeId = null)))
            .erFørstegangsUtbetaling() shouldBe true

        createUtbetaling(utbetalingsLinjer = listOf(createUtbetalingslinje(forrigeUtbetalingslinjeId = UUID30.randomUUID())))
            .erFørstegangsUtbetaling() shouldBe false

        createUtbetaling(
            utbetalingsLinjer = listOf(
                createUtbetalingslinje(forrigeUtbetalingslinjeId = null),
                createUtbetalingslinje(forrigeUtbetalingslinjeId = UUID30.randomUUID())
            )
        ).erFørstegangsUtbetaling() shouldBe true

        createUtbetaling(
            utbetalingsLinjer = listOf(
                createUtbetalingslinje(forrigeUtbetalingslinjeId = UUID30.randomUUID()),
                createUtbetalingslinje(forrigeUtbetalingslinjeId = UUID30.randomUUID())
            )
        ).erFørstegangsUtbetaling() shouldBe false
    }

    private fun createUtbetaling(
        opprettet: Tidspunkt = Tidspunkt.now(),
        utbetalingsLinjer: List<Utbetalingslinje> = createUtbetalingslinjer()
    ) = Utbetaling.UtbetalingForSimulering(
        utbetalingslinjer = utbetalingsLinjer,
        fnr = fnr,
        opprettet = opprettet,
        type = Utbetaling.UtbetalingsType.NY,
        oppdragId = UUID30.randomUUID(),
        behandler = NavIdentBruker.Saksbehandler("Z123"),
        avstemmingsnøkkel = Avstemmingsnøkkel()
    )

    private fun createUtbetalingslinje(
        fraOgMed: LocalDate = 1.januar(2020),
        tilOgMed: LocalDate = 31.desember(2020),
        beløp: Int = 500,
        forrigeUtbetalingslinjeId: UUID30? = null
    ) = Utbetalingslinje(
        fraOgMed = fraOgMed,
        tilOgMed = tilOgMed,
        beløp = beløp,
        forrigeUtbetalingslinjeId = forrigeUtbetalingslinjeId
    )

    private fun createUtbetalingslinjer() = listOf(
        createUtbetalingslinje(
            fraOgMed = 1.januar(2019),
            tilOgMed = 30.april(2020)
        ),
        createUtbetalingslinje(
            fraOgMed = 1.mai(2020),
            tilOgMed = 31.august(2020),
        ),
        createUtbetalingslinje(
            fraOgMed = 1.september(2020),
            tilOgMed = 31.januar(2021),
        )
    )
}
