package no.nav.su.se.bakover.domain.utbetaling.stans

import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.capture
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding.Oppdragsmeldingstatus.SENDT
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.internal.verification.Times

internal class StansUtbetalingServiceTest {

    @Test
    fun `stans utbetalinger`() {
        val setup = Setup()

        val utbetalingPersistenceObserverMock = mock<UtbetalingPersistenceObserver> {
            on { addSimulering(setup.utbetalingUtenSimulering.id, setup.nySimulering) } doReturn setup.nySimulering.copy()
            on { addOppdragsmelding(any(), any()) } doReturn Oppdragsmelding(SENDT, "")
        }

        val capturedOpprettUtbetalingArgument = ArgumentCaptor.forClass(Utbetaling::class.java)
        val oppdragPersistenceObserverMock = mock<Oppdrag.OppdragPersistenceObserver> {
            on { opprettUtbetaling(eq(setup.oppdragId), capture<Utbetaling>(capturedOpprettUtbetalingArgument)) } doReturn setup.utbetalingUtenSimulering.copy().apply {
                addObserver(utbetalingPersistenceObserverMock)
            }
            on { hentFnr(setup.sakId) } doReturn setup.fnr
        }

        val capturedSimuleringArgument = ArgumentCaptor.forClass(NyUtbetaling::class.java)
        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(capture<NyUtbetaling>(capturedSimuleringArgument)) } doReturn setup.nySimulering.copy().right()
        }

        val capturedUtbetalingArgument = ArgumentCaptor.forClass(NyUtbetaling::class.java)
        val publisherMock = mock<UtbetalingPublisher> {
            on { publish(capture<NyUtbetaling>(capturedUtbetalingArgument)) } doReturn "".right()
        }

        val service = StansUtbetalingService(
            simuleringClient = simuleringClientMock,
            clock = setup.clock,
            utbetalingPublisher = publisherMock
        )

        setup.eksisterendeSak.oppdrag.apply {
            addObserver(oppdragPersistenceObserverMock)
        }

        service.stansUtbetalinger(
            sak = setup.eksisterendeSak
        ) shouldBe setup.utbetalingMedSimulering.copy(
            oppdragsmelding = Oppdragsmelding(status = SENDT, "")
        ).right()

        val forventetNyUtbetaling = NyUtbetaling(
            oppdrag = setup.eksisterendeOppdrag,
            utbetaling = setup.utbetalingUtenSimulering.copy(
                id = capturedSimuleringArgument.value.utbetaling.id,
                opprettet = capturedSimuleringArgument.value.utbetaling.opprettet,
                utbetalingslinjer = listOf(
                    Utbetalingslinje(
                        id = capturedSimuleringArgument.value.utbetaling.utbetalingslinjer[0].id,
                        opprettet = capturedSimuleringArgument.value.utbetaling.utbetalingslinjer[0].opprettet,
                        fom = LocalDate.of(1970, 2, 1),
                        tom = LocalDate.of(1971, 1, 1),
                        forrigeUtbetalingslinjeId = setup.existerendeUtbetaling.utbetalingslinjer[0].id,
                        beløp = 0
                    )
                )
            ),
            Attestant("SU")
        )
        capturedSimuleringArgument.value shouldBe forventetNyUtbetaling
        capturedUtbetalingArgument.value shouldBe forventetNyUtbetaling

        // verify(simuleringClientMock, Times(1)).simulerUtbetaling(NyUtbetaling(oppdrag = setup.eksisterendeOppdrag, utbetaling = setup.utbetaling, Attestant("SU")))
        // verify(publisherMock, Times(1)).publish(forventetNyUtbetaling)
        verify(oppdragPersistenceObserverMock, Times(1)).opprettUtbetaling(eq(setup.oppdragId), any())
    }

    private data class Setup(
        val clock: Clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        val sakId: UUID = UUID.randomUUID(),
        val fnr: Fnr = Fnr("12345678910"),
        val oppdragId: UUID30 = UUID30.randomUUID(),
        val utbetalingId: UUID30 = UUID30.randomUUID(),
        val saksbehandler: Saksbehandler = Saksbehandler("saksbehandler"),
        val existerendeUtbetaling: Utbetaling = Utbetaling(
            id = UUID30.randomUUID(),
            opprettet = Instant.EPOCH,
            simulering = Simulering(
                gjelderId = fnr,
                gjelderNavn = "",
                datoBeregnet = LocalDate.EPOCH,
                nettoBeløp = 10000,
                periodeList = listOf()
            ),
            kvittering = Kvittering(
                utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                originalKvittering = "<someXml></someXml>",
                mottattTidspunkt = Instant.EPOCH
            ),
            oppdragsmelding = Oppdragsmelding(SENDT, ""),
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    id = UUID30.randomUUID(),
                    opprettet = Instant.EPOCH,
                    fom = LocalDate.EPOCH,
                    tom = LocalDate.EPOCH.plusMonths(12),
                    forrigeUtbetalingslinjeId = null,
                    beløp = 10000
                )
            ),
            avstemmingId = null,
            fnr = fnr
        ),
        val eksisterendeOppdrag: Oppdrag = Oppdrag(
            id = oppdragId,
            opprettet = Instant.EPOCH,
            sakId = sakId,
            utbetalinger = mutableListOf(
                existerendeUtbetaling
            )
        ),
        val nySimulering: Simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "",
            datoBeregnet = LocalDate.EPOCH,
            nettoBeløp = 0,
            periodeList = listOf()
        ),
        val utbetalingUtenSimulering: Utbetaling = Utbetaling(
            id = utbetalingId,
            opprettet = Instant.EPOCH,
            simulering = null,
            kvittering = null,
            oppdragsmelding = null,
            utbetalingslinjer = listOf(),
            avstemmingId = null,
            fnr = fnr
        ),
        val utbetalingMedSimulering: Utbetaling = utbetalingUtenSimulering.copy(
            simulering = nySimulering
        ),
        val eksisterendeSak: Sak = Sak(
            id = sakId,
            opprettet = Instant.EPOCH,
            fnr = fnr,
            oppdrag = eksisterendeOppdrag
        )
    )
}
