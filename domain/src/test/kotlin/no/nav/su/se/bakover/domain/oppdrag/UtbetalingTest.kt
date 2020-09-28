package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.toTidspunkt
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month

internal class UtbetalingTest {

    private lateinit var observer: DummyObserver
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

    @Test
    fun `legger til og lagrer oppdragsmelding`() {
        val utbetaling = createUtbetaling()
        val oppdragsmelding = Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, "some xml")
        utbetaling.addOppdragsmelding(oppdragsmelding)
        utbetaling.getOppdragsmelding() shouldBe oppdragsmelding
        observer.oppdragsmelding shouldBe oppdragsmelding
    }

    private fun createUtbetaling() = Utbetaling(
        utbetalingslinjer = emptyList(),
        fnr = fnr
    ).also {
        observer = DummyObserver()
        it.addObserver(observer)
    }

    @Test
    fun `ikke lov å slette utbetalinger som er forsøkt oversendt oppdrag eller utbetalt`() {
        createUtbetaling().also {
            it.addOppdragsmelding(Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, "some xml"))
            it.erOversendtOppdrag() shouldBe true
        }.let { it.kanSlettes() shouldBe false }
        createUtbetaling().also {
            it.addOppdragsmelding(Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.FEIL, "some xml"))
            it.erOversendtOppdrag() shouldBe false
        }.let { it.kanSlettes() shouldBe false }
        createUtbetaling().also {
            it.addKvittering(Kvittering(Kvittering.Utbetalingsstatus.OK, ""))
            it.erKvittert() shouldBe true
            it.erKvittertOk() shouldBe true
        }.let { it.kanSlettes() shouldBe false }
        createUtbetaling().also {
            it.addKvittering(Kvittering(Kvittering.Utbetalingsstatus.OK_MED_VARSEL, ""))
            it.erKvittert() shouldBe true
            it.erKvittertOk() shouldBe true
        }.let { it.kanSlettes() shouldBe false }
        createUtbetaling().also {
            it.addKvittering(Kvittering(Kvittering.Utbetalingsstatus.FEIL, ""))
            it.erKvittert() shouldBe true
            it.erKvittertOk() shouldBe false
        }.let { it.kanSlettes() shouldBe false }
    }

    private class DummyObserver : UtbetalingPersistenceObserver {
        lateinit var simulering: Simulering
        lateinit var kvittering: Kvittering
        lateinit var oppdragsmelding: Oppdragsmelding

        override fun addSimulering(utbetalingId: UUID30, simulering: Simulering) {
            this.simulering = simulering
        }

        override fun addKvittering(utbetalingId: UUID30, kvittering: Kvittering): Kvittering {
            this.kvittering = kvittering
            return kvittering
        }

        override fun addOppdragsmelding(utbetalingId: UUID30, oppdragsmelding: Oppdragsmelding): Oppdragsmelding {
            this.oppdragsmelding = oppdragsmelding
            return oppdragsmelding
        }
    }
}
