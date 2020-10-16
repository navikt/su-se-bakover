package no.nav.su.se.bakover.service.utbetaling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.database.sak.SakRepo
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Sats
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Oppdragsmelding
import no.nav.su.se.bakover.domain.oppdrag.OversendelseTilOppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingPublisher
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.util.UUID

internal class UtbetalingServiceImplTest {

    @Test
    fun `hent utbetaling - ikke funnet`() {
        val utbetalingRepoMock = mock<UtbetalingRepo> { on { hentUtbetaling(any<UUID30>()) } doReturn null }

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakRepo = mock(),
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
            behandler = NavIdentBruker.Saksbehandler("Z123")
        )
        val utbetalingRepoMock = mock<UtbetalingRepo> { on { hentUtbetaling(any<UUID30>()) } doReturn utbetaling }

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakRepo = mock(),
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
            sakRepo = mock(),
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
        val avstemmingsnøkkel = Avstemmingsnøkkel()
        val utbetaling = Utbetaling.OversendtUtbetaling(
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910"),
            oppdragsmelding = Oppdragsmelding("", avstemmingsnøkkel),
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
            on { hentUtbetaling(avstemmingsnøkkel) } doReturn utbetaling
            on { oppdaterMedKvittering(utbetaling.id, kvittering) } doReturn postUpdate
        }

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            sakRepo = mock(),
            simuleringClient = mock(),
            utbetalingPublisher = mock()
        ).oppdaterMedKvittering(
            utbetaling.oppdragsmelding.avstemmingsnøkkel,
            kvittering
        ) shouldBe postUpdate.right()

        verify(utbetalingRepoMock, Times(1)).hentUtbetaling(avstemmingsnøkkel)
        verify(utbetalingRepoMock, Times(1)).oppdaterMedKvittering(utbetaling.id, kvittering)
    }

    @Test
    fun `oppdater med kvittering - kvittering eksisterer fra før`() {
        val avstemmingsnøkkel = Avstemmingsnøkkel()
        val utbetaling = Utbetaling.KvittertUtbetaling(
            utbetalingslinjer = listOf(),
            fnr = Fnr("12345678910"),
            oppdragsmelding = Oppdragsmelding("", avstemmingsnøkkel),
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
            sakRepo = mock(),
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
    fun `lag utbetaling for simulering`() {
        val sakId = UUID.randomUUID()
        val fnr = Fnr("12345678910")
        val beregning = Beregning(
            fraOgMed = 1.januar(2020),
            tilOgMed = 31.januar(2020),
            sats = Sats.HØY,
            fradrag = emptyList()
        )
        val sak = Sak(
            id = sakId, fnr = fnr,
            oppdrag = Oppdrag(
                id = UUID30.randomUUID(), opprettet = Tidspunkt.now(), sakId = sakId, utbetalinger = mutableListOf()
            )
        )

        val sakRepoMock = mock<SakRepo> {
            on { hentSak(sakId) } doReturn sak
        }

        val response = UtbetalingServiceImpl(
            utbetalingRepo = mock(),
            sakRepo = sakRepoMock,
            simuleringClient = mock(),
            utbetalingPublisher = mock()
        ).lagUtbetaling(
            sak.id,
            Oppdrag.UtbetalingStrategy.Ny(
                behandler = NavIdentBruker.Saksbehandler("Z123"),
                beregning = beregning
            )
        )

        verify(sakRepoMock).hentSak(sakId)
        response.utbetaling shouldNotBe null
    }

    @Test
    fun `utbetaler penger og lagrer utbetaling`() {
        val utbetalingRepoMock = mock<UtbetalingRepo> {
            on { opprettUtbetaling(oversendtUtbetaling) } doReturn oversendtUtbetaling
        }
        val utbetalingPublisherMock = mock<UtbetalingPublisher>() {
            on { publish(tilUtbetaling) } doReturn oppdragsmelding.right()
        }

        UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            utbetalingPublisher = utbetalingPublisherMock,
            sakRepo = mock(),
            simuleringClient = mock()
        ).utbetal(tilUtbetaling)

        inOrder(
            utbetalingPublisherMock,
            utbetalingRepoMock
        ) {
            verify(utbetalingPublisherMock).publish(tilUtbetaling)
            verify(utbetalingRepoMock).opprettUtbetaling(oversendtUtbetaling)
        }
    }

    @Test
    fun `returnerer feilmelding dersom utbetaling feiler`() {
        val utbetalingRepoMock = mock<UtbetalingRepo>()
        val utbetalingPublisherMock = mock<UtbetalingPublisher>() {
            on { publish(tilUtbetaling) } doReturn UtbetalingPublisher.KunneIkkeSendeUtbetaling(
                Oppdragsmelding(
                    originalMelding = "adadad",
                    avstemmingsnøkkel = avstemmingsnøkkel
                )
            ).left()
        }

        val response = UtbetalingServiceImpl(
            utbetalingRepo = utbetalingRepoMock,
            utbetalingPublisher = utbetalingPublisherMock,
            sakRepo = mock(),
            simuleringClient = mock()
        ).utbetal(tilUtbetaling)

        response shouldBe UtbetalingFeilet.left()
        verify(utbetalingPublisherMock).publish(tilUtbetaling)
        verifyZeroInteractions(utbetalingRepoMock)
    }

    private val oppdrag = Oppdrag(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = UUID.randomUUID(),
    )

    private val avstemmingsnøkkel = Avstemmingsnøkkel()

    private val oppdragsmelding = Oppdragsmelding(
        originalMelding = "",
        avstemmingsnøkkel = avstemmingsnøkkel
    )

    private val simulertUtbetaling = Utbetaling.SimulertUtbetaling(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(),
        fnr = Fnr("12345678910"),
        utbetalingslinjer = listOf(),
        type = Utbetaling.UtbetalingsType.NY,
        simulering = Simulering(
            gjelderId = Fnr("12345678910"),
            gjelderNavn = "navn",
            datoBeregnet = idag(),
            nettoBeløp = 5155,
            periodeList = listOf()
        ),
        oppdragId = oppdrag.id,
        behandler = NavIdentBruker.Attestant("SU")
    )

    private val oversendtUtbetaling = simulertUtbetaling.toOversendtUtbetaling(oppdragsmelding)

    private val tilUtbetaling = OversendelseTilOppdrag.TilUtbetaling(
        utbetaling = simulertUtbetaling,
        avstemmingsnøkkel = avstemmingsnøkkel
    )
}
