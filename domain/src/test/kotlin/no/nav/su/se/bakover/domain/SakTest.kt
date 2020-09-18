package no.nav.su.se.bakover.domain

import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.UUIDFactory
import no.nav.su.se.bakover.domain.behandlinger.stopp.Stoppbehandling
import no.nav.su.se.bakover.domain.behandlinger.stopp.StoppbehandlingFactory
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingPersistenceObserver
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringClient
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

internal class SakTest {

    private val clock = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)
    private val sakId = UUID.randomUUID()
    private val fnr = Fnr("12345678910")

    private lateinit var persistenceObserver: PersistenceObserver
    private lateinit var eventObserver: EventObserver
    private lateinit var sak: Sak

    @Test
    fun `should handle nySøknad`() {
        val søknad = sak.nySøknad(SøknadInnholdTestdataBuilder.build())

        persistenceObserver.nySøknadParams.søknad shouldBeSameInstanceAs søknad
        persistenceObserver.nySøknadParams.sakId shouldBe sak.id
        eventObserver.events shouldHaveSize 1
        sak.behandlinger() shouldHaveSize 0
        sak.søknader() shouldHaveSize 1
    }

    @Test
    fun `should handle opprettSøknadsbehandling`() {
        val søknad = sak.nySøknad(SøknadInnholdTestdataBuilder.build())
        val behandling = sak.opprettSøknadsbehandling(søknad.id)

        persistenceObserver.opprettSøknadsbehandlingParams.behandling shouldBeSameInstanceAs behandling
        persistenceObserver.opprettSøknadsbehandlingParams.sakId shouldBe sak.id
        sak.behandlinger() shouldHaveSize 1
        behandling.søknad.id shouldBe søknad.id
    }

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
        val sakPersistenceObserverMock = mock<SakPersistenceObserver> {
            on { nyStoppbehandling(simulertStoppbehandling) } doReturn simulertStoppbehandling
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

        val sak = nySak().copy(
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
        ).apply {
            addObserver(sakPersistenceObserverMock)
        }
        val simuleringClientMock = mock<SimuleringClient> {
            on { simulerUtbetaling(any()) } doReturn nySimulering.right()
        }
        val uuidFactoryMock = mock<UUIDFactory> {
            on { newUUID() } doReturn simulertStoppbehandlingId
        }
        val stoppbehandlingFactory = StoppbehandlingFactory(
            simuleringClient = simuleringClientMock,
            clock = clock,
            uuidFactory = uuidFactoryMock
        )

        sak.stoppUtbetalinger(
            stoppbehandlingFactory = stoppbehandlingFactory,
            saksbehandler = saksbehandler,
            stoppÅrsak = stoppÅrsak
        ) shouldBe simulertStoppbehandling.right()

        verify(sakPersistenceObserverMock, Times(1)).nyStoppbehandling(simulertStoppbehandling)
        // Oppdrag.genererUtbetaling inneholder ikke-forutsigbareverdier (UUID, Instant)
        verify(oppdragPersistenceObserverMock, Times(1)).opprettUtbetaling(eq(oppdragId), any())
    }

    class PersistenceObserver : SakPersistenceObserver {
        lateinit var nySøknadParams: NySøknadParams
        override fun nySøknad(sakId: UUID, søknad: Søknad) = søknad.also {
            nySøknadParams = NySøknadParams(sakId, søknad)
        }

        lateinit var opprettSøknadsbehandlingParams: OpprettSøknadsbehandlingParams
        override fun opprettSøknadsbehandling(sakId: UUID, behandling: Behandling) = behandling.also {
            opprettSøknadsbehandlingParams = OpprettSøknadsbehandlingParams(sakId, behandling)
        }

        data class NySøknadParams(
            val sakId: UUID,
            val søknad: Søknad
        )

        data class OpprettSøknadsbehandlingParams(
            val sakId: UUID,
            val behandling: Behandling
        )

        override fun nyStoppbehandling(nyBehandling: Stoppbehandling.Simulert) = throw NotImplementedError()
        override fun hentPågåendeStoppbehandling(sakId: UUID) = throw NotImplementedError()
    }

    class EventObserver : SakEventObserver {
        val events = mutableListOf<SakEventObserver.NySøknadEvent>()
        override fun nySøknadEvent(nySøknadEvent: SakEventObserver.NySøknadEvent) {
            events.add(nySøknadEvent)
        }
    }

    @BeforeEach
    fun beforeEach() {
        persistenceObserver = PersistenceObserver()
        eventObserver = EventObserver()

        sak = nySak().also {
            it.addObserver(persistenceObserver)
            it.addObserver(eventObserver)
        }
    }

    private fun nySak() = Sak(id = sakId, opprettet = Instant.EPOCH, fnr = fnr, oppdrag = Oppdrag(sakId = sakId))
}
