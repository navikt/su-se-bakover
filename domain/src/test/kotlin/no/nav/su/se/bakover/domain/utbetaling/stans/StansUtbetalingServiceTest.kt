package no.nav.su.se.bakover.domain.utbetaling.stans

import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.capture
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
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
import org.mockito.stubbing.Answer

internal class StansUtbetalingServiceTest {

    @Test
    fun `stans utbetalinger`() {
        val setup = Setup()

        val utbetalingPersistenceObserverMock = mock<UtbetalingPersistenceObserver> {
            on {
                addOppdragsmelding(
                    any(), // TODO: utbetalingsid blir generert statisk, så vi har ingen måte å styre den på atm.
                    eq(setup.oppdragsmeldingSendt)
                )
            } doReturn setup.oppdragsmeldingSendt
        }

        val oppdragPersistenceObserverMock = mock<Oppdrag.OppdragPersistenceObserver> {

            on {
                opprettUtbetaling(eq(setup.oppdragId), any())
            } doAnswer (
                Answer { invocation ->
                    val actualUtbetaling: Utbetaling = invocation!!.getArgument(1)
                    actualUtbetaling shouldBe setup.forventetUtbetaling(actualUtbetaling)
                    actualUtbetaling.apply {
                        addObserver(utbetalingPersistenceObserverMock)
                    }
                }
                )

            on { hentFnr(setup.sakId) } doReturn setup.fnr
        }

        val capturedSimuleringArgument = ArgumentCaptor.forClass(NyUtbetaling::class.java)
        val simuleringClientMock = mock<SimuleringClient> {
            on {
                simulerUtbetaling(capture<NyUtbetaling>(capturedSimuleringArgument))
            } doAnswer (
                Answer { invocation ->
                    val actualNyUtbetaling: NyUtbetaling = invocation!!.getArgument(0)
                    actualNyUtbetaling shouldBe setup.forventetNyUtbetaling(actualNyUtbetaling.utbetaling)
                    setup.nySimulering.right()
                }
                )
        }

        val capturedUtbetalingArgument = ArgumentCaptor.forClass(NyUtbetaling::class.java)
        val publisherMock = mock<UtbetalingPublisher> {
            on {
                publish(capture<NyUtbetaling>(capturedUtbetalingArgument))
            } doAnswer (
                Answer { invocation ->
                    val actualNyUtbetaling: NyUtbetaling = invocation!!.getArgument(0)
                    actualNyUtbetaling shouldBe setup.forventetNyUtbetaling(
                        actualNyUtbetaling.utbetaling,
                        setup.nySimulering
                    )
                    "".right()
                }
                )
        }

        val service = StansUtbetalingService(
            simuleringClient = simuleringClientMock,
            clock = setup.clock,
            utbetalingPublisher = publisherMock
        )

        setup.eksisterendeSak.oppdrag.apply {
            addObserver(oppdragPersistenceObserverMock)
        }

        val actualResponse = service.stansUtbetalinger(sak = setup.eksisterendeSak)

        actualResponse shouldBe setup.forventetUtbetaling(
            capturedUtbetalingArgument.value.utbetaling,
            setup.nySimulering,
            setup.oppdragsmeldingSendt
        ).right()

        verify(oppdragPersistenceObserverMock, Times(1)).opprettUtbetaling(eq(setup.oppdragId), any())
    }

    private data class Setup(
        val clock: Clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        val sakId: UUID = UUID.randomUUID(),
        val fnr: Fnr = Fnr("12345678910"),
        val attestant: Attestant = Attestant("SU"),
        val oppdragId: UUID30 = UUID30.randomUUID(),
        val utbetalingId: UUID30 = UUID30.randomUUID(),
        val eksisterendeUtbetaling: Utbetaling = Utbetaling(
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
                eksisterendeUtbetaling
            )
        ),
        val nySimulering: Simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "",
            datoBeregnet = LocalDate.EPOCH,
            nettoBeløp = 0,
            periodeList = listOf()
        ),
        val eksisterendeSak: Sak = Sak(
            id = sakId,
            opprettet = Instant.EPOCH,
            fnr = fnr,
            oppdrag = eksisterendeOppdrag
        ),
        val oppdragsmeldingSendt: Oppdragsmelding = Oppdragsmelding(SENDT, "")
    ) {

        fun forventetNyUtbetaling(
            actualUtbetaling: Utbetaling,
            simulering: Simulering? = null,
            oppdragsmelding: Oppdragsmelding? = null
        ) = NyUtbetaling(
            oppdrag = eksisterendeOppdrag,
            utbetaling = forventetUtbetaling(actualUtbetaling, simulering, oppdragsmelding),
            attestant = attestant
        )

        /**
         * En liten hack for å omgå at vi mangler kontroll over UUID/Instant
         */
        fun forventetUtbetaling(
            actualUtbetaling: Utbetaling,
            simulering: Simulering? = null,
            oppdragsmelding: Oppdragsmelding? = null
        ) = Utbetaling(
            id = actualUtbetaling.id,
            opprettet = actualUtbetaling.opprettet,
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    id = actualUtbetaling.utbetalingslinjer[0].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[0].opprettet,
                    fom = LocalDate.of(1970, 2, 1),
                    tom = LocalDate.of(1971, 1, 1),
                    forrigeUtbetalingslinjeId = eksisterendeUtbetaling.utbetalingslinjer[0].id,
                    beløp = 0
                ),
            ),
            fnr = fnr,
            simulering = simulering,
            oppdragsmelding = oppdragsmelding,
        )
    }
}
