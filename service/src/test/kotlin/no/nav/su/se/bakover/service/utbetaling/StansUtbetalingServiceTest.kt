package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.capture
import com.nhaarman.mockitokotlin2.doAnswer
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
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
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher.KunneIkkeSendeUtbetaling
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.internal.verification.Times
import org.mockito.stubbing.Answer
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal class StansUtbetalingServiceTest {

    @Test
    fun `stans utbetalinger`() {
        val setup = Setup()
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

        val capturedOpprettUtbetaling = ArgumentCaptor.forClass(Utbetaling.Stans::class.java)
        val capturedAddSimulering = ArgumentCaptor.forClass(Simulering::class.java)
        val capturedAddOppdragsmelding = ArgumentCaptor.forClass(Oppdragsmelding::class.java)
        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                opprettUtbetaling(any(), capture<Utbetaling.Stans>(capturedOpprettUtbetaling))
            } doAnswer { capturedOpprettUtbetaling.value }
            on {
                addSimulering(any(), capture<Simulering>(capturedAddSimulering))
            } doAnswer {
                capturedOpprettUtbetaling.value.copy(
                    simulering = capturedAddSimulering.value
                )
            }
            on {
                addOppdragsmelding(any(), capture<Oppdragsmelding>(capturedAddOppdragsmelding))
            } doAnswer {
                capturedOpprettUtbetaling.value.copy(
                    simulering = capturedAddSimulering.value,
                    oppdragsmelding = capturedAddOppdragsmelding.value
                )
            }
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
                    setup.oppdragsmeldingSendt.right()
                }
                )
        }

        val service = StansUtbetalingService(
            simuleringClient = simuleringClientMock,
            clock = setup.clock,
            utbetalingPublisher = publisherMock,
            utbetalingService = utbetalingServiceMock
        )

        val actualResponse = service.stansUtbetalinger(sak = setup.eksisterendeSak)

        actualResponse shouldBe setup.forventetUtbetaling(
            capturedUtbetalingArgument.value.utbetaling,
            setup.nySimulering,
            setup.oppdragsmeldingSendt
        ).right()

        verify(simuleringClientMock, Times(1)).simulerUtbetaling(any())
        verify(publisherMock, Times(1)).publish(any())
        verify(utbetalingServiceMock, Times(1)).opprettUtbetaling(eq(setup.oppdragId), any())
        verify(utbetalingServiceMock, Times(1)).addSimulering(any(), any())
        verify(utbetalingServiceMock, Times(1)).addOppdragsmelding(any(), any())

        verifyNoMoreInteractions(simuleringClientMock, publisherMock, utbetalingServiceMock)
    }

    @Test
    fun `Sjekk at vi svarer furnuftig når simulering feiler`() {
        val setup = Setup()

        val simuleringClientMock = mock<SimuleringClient> {
            on {
                simulerUtbetaling(any())
            } doAnswer (
                Answer {
                    SimuleringFeilet.TEKNISK_FEIL.left()
                }
                )
        }

        val utbetalingServiceMock: UtbetalingService = mock()

        val service = StansUtbetalingService(
            simuleringClient = simuleringClientMock,
            clock = setup.clock,
            utbetalingPublisher = mock(),
            utbetalingService = utbetalingServiceMock
        )

        val actualResponse = service.stansUtbetalinger(sak = setup.eksisterendeSak)

        actualResponse shouldBe StansUtbetalingService.KunneIkkeStanseUtbetalinger.left()

        verify(simuleringClientMock, Times(1)).simulerUtbetaling(any())

        verifyNoMoreInteractions(simuleringClientMock, utbetalingServiceMock)
    }

    @Test
    fun `svarer med feil dersom simulering inneholder beløp større enn 0`() {
        val setup = Setup()

        val simuleringClientMock = mock<SimuleringClient> {
            on {
                simulerUtbetaling(any())
            } doAnswer (
                Answer {
                    setup.nySimulering.copy(nettoBeløp = 6000).right()
                }
                )
        }

        val utbetalingServiceMock: UtbetalingService = mock()

        val service = StansUtbetalingService(
            simuleringClient = simuleringClientMock,
            clock = setup.clock,
            utbetalingPublisher = mock(),
            utbetalingService = utbetalingServiceMock
        )

        val actualResponse = service.stansUtbetalinger(sak = setup.eksisterendeSak)

        actualResponse shouldBe StansUtbetalingService.KunneIkkeStanseUtbetalinger.left()

        verify(simuleringClientMock, Times(1)).simulerUtbetaling(any())

        verifyNoMoreInteractions(simuleringClientMock, utbetalingServiceMock)
    }

    @Test
    fun `Sjekk at vi svarer furnuftig når publisering feiler`() {
        val setup = Setup()
        val simuleringClientMock = mock<SimuleringClient> {
            on {
                simulerUtbetaling(any())
            } doAnswer (
                Answer {
                    setup.nySimulering.right()
                }
                )
        }

        val capturedOpprettUtbetaling = ArgumentCaptor.forClass(Utbetaling.Stans::class.java)
        val capturedAddSimulering = ArgumentCaptor.forClass(Simulering::class.java)
        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                opprettUtbetaling(any(), capture<Utbetaling.Stans>(capturedOpprettUtbetaling))
            } doAnswer { capturedOpprettUtbetaling.value }
            on {
                addSimulering(any(), capture<Simulering>(capturedAddSimulering))
            } doAnswer {
                capturedOpprettUtbetaling.value.copy(
                    simulering = capturedAddSimulering.value
                )
            }
        }

        val publisherMock = mock<UtbetalingPublisher> {
            on {
                publish(any())
            } doAnswer (
                Answer {
                    KunneIkkeSendeUtbetaling(Oppdragsmelding(SENDT, "", Avstemmingsnøkkel())).left()
                }
                )
        }

        val service = StansUtbetalingService(
            simuleringClient = simuleringClientMock,
            clock = setup.clock,
            utbetalingPublisher = publisherMock,
            utbetalingService = utbetalingServiceMock
        )

        val actualResponse = service.stansUtbetalinger(sak = setup.eksisterendeSak)

        actualResponse shouldBe StansUtbetalingService.KunneIkkeStanseUtbetalinger.left()

        verify(simuleringClientMock, Times(1)).simulerUtbetaling(any())
        verify(publisherMock, Times(1)).publish(any())
        verify(utbetalingServiceMock, Times(1)).opprettUtbetaling(eq(setup.oppdragId), any())
        verify(utbetalingServiceMock, Times(1)).addSimulering(any(), any())
        verify(utbetalingServiceMock, Times(1)).addOppdragsmelding(any(), any())

        verifyNoMoreInteractions(simuleringClientMock, publisherMock, utbetalingServiceMock)
    }

    private data class Setup(
        val clock: Clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
        val sakId: UUID = UUID.randomUUID(),
        val fnr: Fnr = Fnr("12345678910"),
        val attestant: Attestant = Attestant("SU"),
        val oppdragId: UUID30 = UUID30.randomUUID(),
        val utbetalingId: UUID30 = UUID30.randomUUID(),
        val eksisterendeUtbetaling: Utbetaling = Utbetaling.Ny(
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
            oppdragsmelding = Oppdragsmelding(SENDT, "", Avstemmingsnøkkel()),
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    id = UUID30.randomUUID(),
                    opprettet = Tidspunkt.EPOCH,
                    fraOgMed = LocalDate.EPOCH,
                    tilOgMed = LocalDate.EPOCH.plusMonths(12),
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
        val oppdragsmeldingSendt: Oppdragsmelding = Oppdragsmelding(SENDT, "", Avstemmingsnøkkel()),
        val oppdragsmeldingFeil: Oppdragsmelding = Oppdragsmelding(FEIL, "", Avstemmingsnøkkel())
    ) {

        fun forventetNyUtbetaling(
            actualUtbetaling: Utbetaling,
            simulering: Simulering? = null,
            oppdragsmelding: Oppdragsmelding? = null
        ) = NyUtbetaling(
            oppdrag = eksisterendeOppdrag,
            utbetaling = forventetUtbetaling(actualUtbetaling, simulering, oppdragsmelding),
            attestant = attestant,
            avstemmingsnøkkel = Avstemmingsnøkkel(Tidspunkt.now(clock))
        )

        /**
         * En liten hack for å omgå at vi mangler kontroll over UUID/Instant
         */
        fun forventetUtbetaling(
            actualUtbetaling: Utbetaling,
            simulering: Simulering? = null,
            oppdragsmelding: Oppdragsmelding? = null
        ) = Utbetaling.Stans(
            id = actualUtbetaling.id,
            opprettet = actualUtbetaling.opprettet,
            utbetalingslinjer = listOf(
                Utbetalingslinje(
                    id = actualUtbetaling.utbetalingslinjer[0].id,
                    opprettet = actualUtbetaling.utbetalingslinjer[0].opprettet,
                    fraOgMed = LocalDate.of(1970, 2, 1),
                    tilOgMed = LocalDate.of(1971, 1, 1),
                    forrigeUtbetalingslinjeId = eksisterendeUtbetaling.utbetalingslinjer[0].id,
                    beløp = 0
                )
            ),
            fnr = fnr,
            simulering = simulering,
            oppdragsmelding = oppdragsmelding,
        )
    }
}
