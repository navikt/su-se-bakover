package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month

internal class UtbetalingTest {

    private val fnr = Fnr("12345678910")

    @Test
    fun `sorts utbetalinger ascending by opprettet`() {
        val first = createUtbetaling(opprettet = LocalDate.of(2020, Month.APRIL, 1).atStartOfDay().toTidspunkt())
        val second = createUtbetaling(opprettet = LocalDate.of(2020, Month.JANUARY, 1).atStartOfDay().toTidspunkt())
        val third = createUtbetaling(opprettet = LocalDate.of(2020, Month.JULY, 1).atStartOfDay().toTidspunkt())
        val sorted = listOf(first, second, third).sortedWith(Utbetaling.Opprettet)
        sorted shouldContainInOrder listOf(second, first, third)
    }

    @Test
    fun `ikke lov å slette utbetalinger som er forsøkt oversendt oppdrag eller utbetalt`() {
        createUtbetaling().copy(
            oppdragsmelding = Oppdragsmelding(
                Oppdragsmelding.Oppdragsmeldingstatus.SENDT,
                "some xml",
                Avstemmingsnøkkel()
            )
        ).also {
            it.erOversendt() shouldBe true
        }.let { it.kanSlettes() shouldBe false }
        createUtbetaling().copy(
            oppdragsmelding = Oppdragsmelding(
                Oppdragsmelding.Oppdragsmeldingstatus.FEIL,
                "some xml",
                Avstemmingsnøkkel()
            )
        ).also {
            it.erOversendt() shouldBe false
        }.let { it.kanSlettes() shouldBe false }
        createUtbetaling(kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, "")).also {
            it.erKvittert() shouldBe true
            it.erKvittertOk() shouldBe true
        }.let { it.kanSlettes() shouldBe false }
        createUtbetaling(kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK_MED_VARSEL, "")).also {
            it.erKvittert() shouldBe true
            it.erKvittertOk() shouldBe true
        }.let { it.kanSlettes() shouldBe false }
        createUtbetaling(kvittering = Kvittering(Kvittering.Utbetalingsstatus.FEIL, "")).also {
            it.erKvittert() shouldBe true
            it.erKvittertOk() shouldBe false
        }.let { it.kanSlettes() shouldBe false }
    }

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
        kvittering: Kvittering? = null,
        utbetalingsLinjer: List<Utbetalingslinje> = createUtbetalingslinjer()
    ) = Utbetaling(
        utbetalingslinjer = utbetalingsLinjer,
        fnr = fnr,
        kvittering = kvittering,
        opprettet = opprettet
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
