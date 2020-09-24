package no.nav.su.se.bakover.domain

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

internal class SakTest {

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

    private fun nySak() = Sak(
        id = sakId,
        opprettet = Instant.EPOCH,
        fnr = fnr,
        oppdrag = Oppdrag(
            id = UUID30.randomUUID(),
            opprettet = Instant.EPOCH,
            sakId = sakId
        )
    )
}
