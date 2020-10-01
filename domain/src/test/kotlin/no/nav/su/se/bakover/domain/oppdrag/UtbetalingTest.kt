package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.domain.Fnr
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month

internal class UtbetalingTest {

    private val fnr = Fnr("12345678910")

    @Test
    fun `sorts utbetalinger ascending by opprettet`() {
        val first = Utbetaling(
            opprettet = LocalDate.of(2020, Month.APRIL, 1).atStartOfDay().toTidspunkt(),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, ""),
            utbetalingslinjer = emptyList(),
            fnr = fnr
        )
        val second = Utbetaling(
            opprettet = LocalDate.of(2020, Month.JANUARY, 1).atStartOfDay().toTidspunkt(),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, ""),
            utbetalingslinjer = emptyList(),
            fnr = fnr
        )
        val third = Utbetaling(
            opprettet = LocalDate.of(2020, Month.JULY, 1).atStartOfDay().toTidspunkt(),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, ""),
            utbetalingslinjer = emptyList(),
            fnr = fnr
        )
        val sorted = listOf(first, second, third).sortedWith(Utbetaling.Opprettet)
        sorted shouldContainInOrder listOf(second, first, third)
    }

    private fun createUtbetaling(kvittering: Kvittering? = null) = Utbetaling(
        utbetalingslinjer = emptyList(),
        fnr = fnr,
        kvittering = kvittering
    )

    @Test
    fun `ikke lov å slette utbetalinger som er forsøkt oversendt oppdrag eller utbetalt`() {
        createUtbetaling().copy(
            oppdragsmelding = Oppdragsmelding(
                Oppdragsmelding.Oppdragsmeldingstatus.SENDT,
                "some xml"
            )
        ).also {
            it.erOversendtOppdrag() shouldBe true
        }.let { it.kanSlettes() shouldBe false }
        createUtbetaling().copy(
            oppdragsmelding = Oppdragsmelding(
                Oppdragsmelding.Oppdragsmeldingstatus.FEIL,
                "some xml"
            )
        ).also {
            it.erOversendtOppdrag() shouldBe false
        }.let { it.kanSlettes() shouldBe false }
        createUtbetaling(Kvittering(Kvittering.Utbetalingsstatus.OK, "")).also {
            it.erKvittert() shouldBe true
            it.erKvittertOk() shouldBe true
        }.let { it.kanSlettes() shouldBe false }
        createUtbetaling(Kvittering(Kvittering.Utbetalingsstatus.OK_MED_VARSEL, "")).also {
            it.erKvittert() shouldBe true
            it.erKvittertOk() shouldBe true
        }.let { it.kanSlettes() shouldBe false }
        createUtbetaling(Kvittering(Kvittering.Utbetalingsstatus.FEIL, "")).also {
            it.erKvittert() shouldBe true
            it.erKvittertOk() shouldBe false
        }.let { it.kanSlettes() shouldBe false }
    }
}
