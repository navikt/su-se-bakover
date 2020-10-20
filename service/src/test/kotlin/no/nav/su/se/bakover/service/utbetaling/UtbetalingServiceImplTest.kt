package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.sak.SakService
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.util.UUID

internal class UtbetalingServiceImplTest {

    @Test
    fun `hent utbetaling - ikke funnet`() {
        val utbetalingRepoMock = mock<UtbetalingRepo> { on { hentUtbetaling(any<UUID30>()) } doReturn null }

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = mock(),
            simuleringClient = mock(),
            utbetalingPublisher = mock()
        ).hentUtbetaling(UUID30.randomUUID()) shouldBe FantIkkeUtbetaling.left()

        verify(utbetalingRepoMock, Times(1)).hentUtbetaling(any<UUID30>())
    }

    @Test
    fun `hent utbetaling - funnet`() {
        val utbetaling = Utbetaling.UtbetalingForSimulering(
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910"),
            type = Utbetaling.UtbetalingsType.NY,
            oppdragId = UUID30.randomUUID(),
            behandler = NavIdentBruker.Saksbehandler("Z123"),
            avstemmingsnøkkel = Avstemmingsnøkkel()
        )
        val utbetalingRepoMock = mock<UtbetalingRepo> { on { hentUtbetaling(any<UUID30>()) } doReturn utbetaling }

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = mock(),
            simuleringClient = mock(),
            utbetalingPublisher = mock()
        ).hentUtbetaling(utbetaling.id) shouldBe utbetaling.right()

        verify(utbetalingRepoMock).hentUtbetaling(utbetaling.id)
    }

    @Test
    fun `oppdater med kvittering - ikke funnet`() {
        val kvittering = Kvittering(
            Kvittering.Utbetalingsstatus.OK,
            ""
        )

        val avstemmingsnøkkel = Avstemmingsnøkkel()

        val utbetalingRepoMock = mock<UtbetalingRepo> { on { hentUtbetaling(avstemmingsnøkkel) } doReturn null }

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = mock(),
            simuleringClient = mock(),
            utbetalingPublisher = mock()
        ).oppdaterMedKvittering(
            avstemmingsnøkkel = avstemmingsnøkkel,
            kvittering = kvittering
        ) shouldBe FantIkkeUtbetaling.left()

        verify(utbetalingRepoMock, Times(1)).hentUtbetaling(avstemmingsnøkkel)
        verify(utbetalingRepoMock, Times(0)).oppdaterMedKvittering(any(), any())
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer ikke fra før`() {
        val utbetaling = Utbetaling.OversendtUtbetaling.UtenKvittering(
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910"),
            utbetalingsrequest = Utbetalingsrequest(""),
            simulering = Simulering(
                gjelderId = Fnr("12345678910"),
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            type = Utbetaling.UtbetalingsType.NY,
            oppdragId = UUID30.randomUUID(),
            behandler = NavIdentBruker.Saksbehandler("Z123")
        )
        val kvittering = Kvittering(
            Kvittering.Utbetalingsstatus.OK,
            ""
        )

        val postUpdate = utbetaling.toKvittertUtbetaling(kvittering)

        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { hentUtbetaling(utbetaling.avstemmingsnøkkel) } doReturn utbetaling
            on { oppdaterMedKvittering(utbetaling.id, kvittering) } doReturn postUpdate
        }

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = mock(),
            simuleringClient = mock(),
            utbetalingPublisher = mock()
        ).oppdaterMedKvittering(
            utbetaling.avstemmingsnøkkel,
            kvittering
        ) shouldBe postUpdate.right()

        verify(utbetalingRepoMock).hentUtbetaling(utbetaling.avstemmingsnøkkel)
        verify(utbetalingRepoMock).oppdaterMedKvittering(utbetaling.id, kvittering)
        verifyNoMoreInteractions(utbetalingRepoMock)
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer fra før`() {
        val avstemmingsnøkkel = Avstemmingsnøkkel()
        val utbetaling = Utbetaling.OversendtUtbetaling.MedKvittering(
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910"),
            utbetalingsrequest = Utbetalingsrequest(""),
            simulering = Simulering(
                gjelderId = Fnr("12345678910"),
                gjelderNavn = "navn",
                datoBeregnet = idag(),
                nettoBeløp = 0,
                periodeList = listOf()
            ),
            type = Utbetaling.UtbetalingsType.NY,
            kvittering = Kvittering(
                utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                originalKvittering = ""
            ),
            oppdragId = UUID30.randomUUID(),
            behandler = NavIdentBruker.Saksbehandler("Z123")
        )

        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { hentUtbetaling(avstemmingsnøkkel) } doReturn utbetaling
        }

        val nyKvittering = Kvittering(
            utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
            originalKvittering = ""
        )

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakService = mock(),
            simuleringClient = mock(),
            utbetalingPublisher = mock()
        ).oppdaterMedKvittering(
            avstemmingsnøkkel,
            nyKvittering
        ) shouldBe utbetaling.right()

        verify(utbetalingRepoMock, Times(1)).hentUtbetaling(avstemmingsnøkkel)
        verify(utbetalingRepoMock, Times(0)).oppdaterMedKvittering(utbetaling.id, nyKvittering)
    }

    @Test
    fun `utbetaler penger og lagrer utbetaling`() {
        val oppdragMock = mock<Oppdrag> {
            on { genererUtbetaling(any(), any()) } doReturn utbetalingTilSimulering
        }

        val sak = sak.copy(oppdrag = oppdragMock)
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { opprettUtbetaling(oversendtUtbetaling) }.doNothing()
        }
        val utbetalingPublisherMock = mock<UtbetalingPublisher>() {
            on {
                publish(argThat { it shouldBe simulertUtbetaling })
            } doReturn oppdragsmelding.right()
        }

        val response = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            utbetalingPublisher = utbetalingPublisherMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock
        ).utbetal(
            sakId = sakId,
            attestant = attestant,
            beregning = beregning,
            simulering = simulering
        )

        response shouldBe oversendtUtbetaling.right()
        inOrder(
            sakServiceMock,
            simuleringClientMock,
            utbetalingPublisherMock,
            utbetalingRepoMock
        ) {
            verify(sakServiceMock).hentSak(sakId)
            verify(simuleringClientMock).simulerUtbetaling(utbetalingTilSimulering)
            verify(utbetalingPublisherMock).publish(
                argThat { it shouldBe simulertUtbetaling }
            )
            verify(utbetalingRepoMock).opprettUtbetaling(oversendtUtbetaling)
        }
    }

    @Test
    fun `returnerer feilmelding dersom utbetaling feiler`() {
        val oppdragMock = mock<Oppdrag> {
            on { genererUtbetaling(any(), any()) } doReturn utbetalingTilSimulering
        }

        val sak = sak.copy(oppdrag = oppdragMock)
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher>() {
            on {
                publish(
                    argThat { it shouldBe simulertUtbetaling }
                )
            } doReturn UtbetalingPublisher.KunneIkkeSendeUtbetaling(oppdragsmelding).left()
        }

        val response = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            utbetalingPublisher = utbetalingPublisherMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock
        ).utbetal(
            sakId = sakId,
            attestant = attestant,
            beregning = beregning,
            simulering = simulering
        )

        response shouldBe KunneIkkeUtbetale.Protokollfeil.left()

        verify(utbetalingPublisherMock).publish(
            argThat { it shouldBe simulertUtbetaling }
        )
        verifyZeroInteractions(utbetalingRepoMock)
    }

    @Test
    fun `returnerer feil dersom kontrollsimulering er ulik innsendt simulering`() {
        val oppdragMock = mock<Oppdrag> {
            on { genererUtbetaling(any(), any()) } doReturn utbetalingTilSimulering
        }

        val sak = sak.copy(oppdrag = oppdragMock)
        val sakServiceMock = mock<SakService> {
            on { hentSak(sakId) } doReturn sak.right()
        }

        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn simulering.copy(nettoBeløp = 1234).right()
        }

        val utbetalingRepoMock = mock<UtbetalingRepo>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher>()

        val response = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            utbetalingPublisher = utbetalingPublisherMock,
            sakService = sakServiceMock,
            simuleringClient = simuleringClientMock
        ).utbetal(
            sakId = sakId,
            attestant = attestant,
            beregning = beregning,
            simulering = simulering
        )

        response shouldBe KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
        inOrder(
            sakServiceMock,
            simuleringClientMock,
            utbetalingPublisherMock,
            utbetalingRepoMock
        ) {
            verify(sakServiceMock).hentSak(sakId)
            verify(simuleringClientMock).simulerUtbetaling(utbetalingTilSimulering)
            verifyZeroInteractions(utbetalingPublisherMock, utbetalingRepoMock)
        }
    }

    private val sakId = UUID.randomUUID()
    private val fnr = Fnr("12345678910")
    private val sak = Sak(
        id = sakId,
        fnr = fnr,
        oppdrag = mock()
    )

    private val beregning = Beregning(
        fraOgMed = 1.januar(2020),
        tilOgMed = 31.desember(2020),
        sats = Sats.HØY,
        fradrag = listOf(),
    )

    private val attestant = NavIdentBruker.Attestant("SU")

    private val avstemmingsnøkkel = Avstemmingsnøkkel()

    private val oppdragsmelding = Utbetalingsrequest(
        value = ""
    )

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "navn",
        datoBeregnet = idag(),
        nettoBeløp = 5155,
        periodeList = listOf()
    )

    private val utbetalingTilSimulering = Utbetaling.UtbetalingForSimulering(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        fnr = fnr,
        utbetalingslinjer = listOf(),
        type = Utbetaling.UtbetalingsType.NY,
        oppdragId = UUID30.randomUUID(),
        behandler = attestant,
        avstemmingsnøkkel = avstemmingsnøkkel
    )

    private val simulertUtbetaling = utbetalingTilSimulering.toSimulertUtbetaling(simulering)
    private val oversendtUtbetaling = simulertUtbetaling.toOversendtUtbetaling(oppdragsmelding)
}
