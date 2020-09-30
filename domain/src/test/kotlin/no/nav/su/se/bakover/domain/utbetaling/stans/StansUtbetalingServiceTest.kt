package no.nav.su.se.bakover.domain.utbetaling.stans

import argShouldBe
import argThat
import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import doAnswer
import doNothing
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.Attestant
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.NyUtbetaling
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding.Oppdragsmeldingstatus.FEIL
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding.Oppdragsmeldingstatus.SENDT
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal class StansUtbetalingServiceTest {

    @Test
    fun `stans utbetalinger`() {
        val setup = Setup()

        val utbetalingPersistenceObserverMock = mock<UtbetalingPersistenceObserver> {
            on {
                addOppdragsmelding(
                    any(), // TODO: utbetalingsid blir generert statisk, så vi har ingen måte å styre den på atm.
                    argShouldBe(setup.oppdragsmeldingSendt)
                )
            } doReturn setup.oppdragsmeldingSendt

            on {
                addSimulering(any(), argShouldBe(setup.nySimulering))
            }.doNothing()
        }

        val oppdragPersistenceObserverMock = mock<Oppdrag.OppdragPersistenceObserver> {

            on {
                opprettUtbetaling(
                    argShouldBe(setup.oppdragId),
                    argThat {
                        it shouldBe setup.forventetUtbetaling(it)
                        it.addObserver(utbetalingPersistenceObserverMock)
                    })
            }.doNothing()

            on { hentFnr(argShouldBe(setup.sakId)) } doReturn setup.fnr
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on {
                simulerUtbetaling(argShouldBe { setup.forventetNyUtbetaling(this.utbetaling) })
            } doReturn setup.nySimulering.right()
        }

        val publisherMock = mock<UtbetalingPublisher> {
            on {
                publish(
                    argShouldBe {
                        setup.forventetNyUtbetaling(
                            this.utbetaling,
                            setup.nySimulering
                        )
                    }
                )
            } doReturn setup.oppdragsmeldingSendt.right()
        }

        val service = StansUtbetalingService(
            simuleringClient = simuleringClientMock,
            clock = setup.clock,
            utbetalingPublisher = publisherMock
        )

        val sak = setup.eksisterendeSak.copy().apply {
            oppdrag.addObserver(oppdragPersistenceObserverMock)
        }

        val actualResponse = service.stansUtbetalinger(sak = sak)

        actualResponse shouldBe setup.forventetUtbetaling(
            actualResponse.orNull()!!,
            setup.nySimulering,
            setup.oppdragsmeldingSendt
        ).right()

        // We can't do object-assertions at this point, because the sak/oppdrag/utbetaling-objects are mutated to its latest state at this point.
        inOrder(
            oppdragPersistenceObserverMock,
            simuleringClientMock,
            utbetalingPersistenceObserverMock,
            publisherMock
        ) {
            verify(oppdragPersistenceObserverMock, Times(1)).hentFnr(any())
            verify(simuleringClientMock, Times(1)).simulerUtbetaling(any())
            verify(oppdragPersistenceObserverMock, Times(1)).opprettUtbetaling(any(), any())
            verify(utbetalingPersistenceObserverMock, Times(1)).addSimulering(any(), any())
            verify(publisherMock, Times(1)).publish(any())
            verify(utbetalingPersistenceObserverMock, Times(1)).addOppdragsmelding(any(), any())
        }
        verifyNoMoreInteractions(
            utbetalingPersistenceObserverMock,
            oppdragPersistenceObserverMock,
            simuleringClientMock,
            publisherMock
        )
    }

    @Test
    fun `Sjekk at vi svarer furnuftig når simulering feiler`() {
        val setup = Setup()

        val oppdragPersistenceObserverMock = mock<Oppdrag.OppdragPersistenceObserver> {
            on { hentFnr(setup.sakId) } doReturn setup.fnr
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
        }

        val service = StansUtbetalingService(
            simuleringClient = simuleringClientMock,
            clock = setup.clock,
            utbetalingPublisher = mock()
        )
        val sak = setup.eksisterendeSak.copy().apply {
            oppdrag.addObserver(oppdragPersistenceObserverMock)
        }
        val actualResponse = service.stansUtbetalinger(sak = sak)

        actualResponse shouldBe StansUtbetalingService.KunneIkkeStanseUtbetalinger.left()

        verify(oppdragPersistenceObserverMock, Times(1)).hentFnr(any())
        verify(simuleringClientMock, Times(1)).simulerUtbetaling(any())

        verifyNoMoreInteractions(oppdragPersistenceObserverMock, simuleringClientMock)
    }

    @Test
    fun `Sjekk at vi svarer furnuftig når publisering feiler`() {
        val setup = Setup()

        val utbetalingPersistenceObserverMock = mock<UtbetalingPersistenceObserver> {
            on {
                addOppdragsmelding(
                    any(), // TODO: utbetalingsid blir generert statisk, så vi har ingen måte å styre den på atm.
                    argShouldBe(setup.oppdragsmeldingFeil)
                )
            } doReturn setup.oppdragsmeldingFeil

            on {
                addSimulering(any(), argShouldBe(setup.nySimulering))
            }.doNothing()
        }

        val oppdragPersistenceObserverMock = mock<Oppdrag.OppdragPersistenceObserver> {
            on {
                opprettUtbetaling(argShouldBe(setup.oppdragId), argShouldBe { setup.forventetUtbetaling(this) })
            } doAnswer { _: UUID30, arg2: Utbetaling -> arg2.addObserver(utbetalingPersistenceObserverMock) } // Ideelt skulle vi gjort doNothing(), men vi må legge på en observer

            on { hentFnr(argShouldBe(setup.sakId)) } doReturn setup.fnr
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on {
                simulerUtbetaling(argShouldBe { setup.forventetNyUtbetaling(this.utbetaling) })
            } doReturn setup.nySimulering.right()
        }

        val publisherMock = mock<UtbetalingPublisher> {
            on {
                publish(
                    argShouldBe {
                        setup.forventetNyUtbetaling(
                            this.utbetaling,
                            setup.nySimulering
                        )
                    }
                )
            } doReturn UtbetalingPublisher.KunneIkkeSendeUtbetaling(setup.oppdragsmeldingFeil).left()
        }

        val service = StansUtbetalingService(
            simuleringClient = simuleringClientMock,
            clock = setup.clock,
            utbetalingPublisher = publisherMock
        )

        val sak = setup.eksisterendeSak.copy().apply {
            oppdrag.addObserver(oppdragPersistenceObserverMock)
        }

        val actualResponse = service.stansUtbetalinger(sak = sak)

        actualResponse shouldBe StansUtbetalingService.KunneIkkeStanseUtbetalinger.left()

        inOrder(
            oppdragPersistenceObserverMock,
            simuleringClientMock,
            utbetalingPersistenceObserverMock,
            publisherMock
        ) {
            verify(oppdragPersistenceObserverMock, Times(1)).hentFnr(any())
            verify(simuleringClientMock, Times(1)).simulerUtbetaling(any())
            verify(oppdragPersistenceObserverMock, Times(1)).opprettUtbetaling(any(), any())
            verify(utbetalingPersistenceObserverMock, Times(1)).addSimulering(any(), any())
            verify(publisherMock, Times(1)).publish(any())
            verify(utbetalingPersistenceObserverMock, Times(1)).addOppdragsmelding(any(), any())
        }
        verifyNoMoreInteractions(
            utbetalingPersistenceObserverMock,
            oppdragPersistenceObserverMock,
            simuleringClientMock,
            publisherMock
        )
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
            opprettet = Tidspunkt.EPOCH,
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
                mottattTidspunkt = Tidspunkt.EPOCH
            ),
            oppdragsmelding = Oppdragsmelding(SENDT, ""),
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    id = UUID30.randomUUID(),
                    opprettet = Tidspunkt.EPOCH,
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
            opprettet = Tidspunkt.EPOCH,
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
            opprettet = Tidspunkt.EPOCH,
            fnr = fnr,
            oppdrag = eksisterendeOppdrag
        ),
        val oppdragsmeldingSendt: Oppdragsmelding = Oppdragsmelding(SENDT, ""),
        val oppdragsmeldingFeil: Oppdragsmelding = Oppdragsmelding(FEIL, "")
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
