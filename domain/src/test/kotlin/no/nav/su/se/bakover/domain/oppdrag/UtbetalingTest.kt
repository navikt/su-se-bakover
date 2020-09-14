package no.nav.su.se.bakover.domain.oppdrag

import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.util.UUID

internal class UtbetalingTest {

    private lateinit var observer: DummyObserver

    @Test
    fun `sorts utbetalinger ascending by opprettet`() {
        val first = Utbetaling(
            opprettet = LocalDate.of(2020, Month.APRIL, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
            behandlingId = UUID.randomUUID(),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, ""),
            utbetalingslinjer = emptyList()
        )
        val second = Utbetaling(
            opprettet = LocalDate.of(2020, Month.JANUARY, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
            behandlingId = UUID.randomUUID(),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, ""),
            utbetalingslinjer = emptyList()
        )
        val third = Utbetaling(
            opprettet = LocalDate.of(2020, Month.JULY, 1).atStartOfDay().toInstant(ZoneOffset.UTC),
            behandlingId = UUID.randomUUID(),
            kvittering = Kvittering(Kvittering.Utbetalingsstatus.OK, ""),
            utbetalingslinjer = emptyList()
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
        behandlingId = UUID.randomUUID(),
        utbetalingslinjer = emptyList()
    ).also {
        observer = DummyObserver()
        it.addObserver(observer)
    }

    @Test
    fun `ikke lov å slette utbetalinger som er forsøkt oversendt oppdrag eller utbetalt`() {
        createUtbetaling().also {
            it.addOppdragsmelding(Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.SENDT, "some xml"))
        }.let { it.kanSlettes() shouldBe false }
        createUtbetaling().also {
            it.addOppdragsmelding(Oppdragsmelding(Oppdragsmelding.Oppdragsmeldingstatus.FEIL, "some xml"))
        }.let { it.kanSlettes() shouldBe false }
        createUtbetaling().also {
            it.addKvittering(Kvittering(Kvittering.Utbetalingsstatus.OK, ""))
        }.let { it.kanSlettes() shouldBe false }
        createUtbetaling().also {
            it.addKvittering(Kvittering(Kvittering.Utbetalingsstatus.OK_MED_VARSEL, ""))
        }.let { it.kanSlettes() shouldBe false }
        createUtbetaling().also {
            it.addKvittering(Kvittering(Kvittering.Utbetalingsstatus.FEIL, ""))
        }.let { it.kanSlettes() shouldBe false }
    }

    private class DummyObserver : UtbetalingPersistenceObserver {
        lateinit var simulering: Simulering
        lateinit var kvittering: Kvittering
        lateinit var oppdragsmelding: Oppdragsmelding

        override fun addSimulering(utbetalingId: UUID30, simulering: Simulering): Simulering {
            this.simulering = simulering
            return simulering
        }

        override fun addKvittering(utbetalingId: UUID30, kvittering: Kvittering): Kvittering {
            this.kvittering = kvittering
            return kvittering
        }

        override fun addOppdragsmelding(utbetalingId: UUID30, oppdragsmelding: Oppdragsmelding): Oppdragsmelding {
            this.oppdragsmelding = oppdragsmelding
            return oppdragsmelding
        }

        override fun addAvstemmingId(utbetalingId: UUID30, avstemmingId: UUID): UUID {
            return avstemmingId
        }
    }
}
