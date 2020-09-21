package no.nav.su.se.bakover.domain.behandlinger.stopp

import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal class StoppbehandlingServiceTest {

    private val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
    private val sakId = UUID.randomUUID()
    private val fnr = Fnr("12345678910")

    @Test
    fun `stopp utbetalinger`() {
        val oppdragId = UUID30.randomUUID()
        val saksbehandler = Saksbehandler("saksbehandler")
        val stoppÅrsak = "stoppÅrsak"

        val nySimulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "",
            datoBeregnet = LocalDate.EPOCH,
            nettoBeløp = 0,
            periodeList = listOf()
        )
        val nyUtbetaling = Utbetaling(
            id = UUID30.randomUUID(),
            opprettet = Instant.EPOCH,
            simulering = nySimulering,
            kvittering = null,
            oppdragsmelding = null,
            utbetalingslinjer = listOf(),
            avstemmingId = null
        )
        val simulertStoppbehandlingId = UUID.fromString("7b0db8ea-0d77-48e0-a8b5-65dddd44287b")
        val simulertStoppbehandling = Stoppbehandling.Simulert(
            id = simulertStoppbehandlingId,
            opprettet = Instant.EPOCH,
            sakId = sakId,
            utbetaling = nyUtbetaling,
            stoppÅrsak = stoppÅrsak,
            saksbehandler = saksbehandler
        )
        val stoppbehandlingRepoMock = mock<StoppbehandlingRepo> {
            on { opprettStoppbehandling(simulertStoppbehandling) } doReturn simulertStoppbehandling
            on { hentPågåendeStoppbehandling(sakId) } doReturn null
        }

        val utbetalingPersistenceObserverMock = mock<UtbetalingPersistenceObserver> {
            on { addSimulering(nyUtbetaling.id, nySimulering) } doReturn nySimulering
        }

        val oppdragPersistenceObserverMock = mock<Oppdrag.OppdragPersistenceObserver> {
            on { opprettUtbetaling(eq(oppdragId), any()) } doReturn nyUtbetaling.apply {
                addObserver(utbetalingPersistenceObserverMock)
            }
        }

        val eksisterendeSak = nySak().copy(
            oppdrag = Oppdrag(
                id = oppdragId,
                opprettet = Instant.EPOCH,
                sakId = sakId,
                utbetalinger = mutableListOf(
                    Utbetaling(
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
                        oppdragsmelding = null,
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
                        avstemmingId = null
                    )
                )
            ).apply {
                addObserver(oppdragPersistenceObserverMock)
            }
        )
        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn nySimulering.right()
        }
        val uuidFactoryMock = mock<UUIDFactory> {
            on { newUUID() } doReturn simulertStoppbehandlingId
        }
        val stoppbehandlingFactory = StoppbehandlingService(
            simuleringClient = simuleringClientMock,
            clock = clock,
            uuidFactory = uuidFactoryMock,
            stoppbehandlingRepo = stoppbehandlingRepoMock
        )

        stoppbehandlingFactory.stoppUtbetalinger(
            sak = eksisterendeSak,
            saksbehandler = saksbehandler,
            stoppÅrsak = stoppÅrsak
        ) shouldBe simulertStoppbehandling.right()

        verify(stoppbehandlingRepoMock, Times(1)).opprettStoppbehandling(simulertStoppbehandling)
        // Oppdrag.genererUtbetaling inneholder ikke-forutsigbareverdier (UUID, Instant)
        verify(oppdragPersistenceObserverMock, Times(1)).opprettUtbetaling(eq(oppdragId), any())
    }

    private fun nySak() = Sak(id = sakId, opprettet = Instant.EPOCH, fnr = fnr, oppdrag = Oppdrag(sakId = sakId))
}
