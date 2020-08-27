package no.nav.su.se.bakover.domain

import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SakTest {

    lateinit var persistenceObserver: PersistenceObserver
    lateinit var eventObserver: EventObserver
    lateinit var sak: Sak

    @Test
    fun `should handle nySøknad`() {
        val søknad = sak.nySøknad(SøknadInnholdTestdataBuilder.build())
        val sakDto = sak.toDto()

        persistenceObserver.nySøknadParams.søknad shouldBeSameInstanceAs søknad
        persistenceObserver.nySøknadParams.sakId shouldBe sakDto.id
        eventObserver.events shouldHaveSize 1
        sakDto.behandlinger shouldHaveSize 0
        sakDto.søknader shouldHaveSize 1
    }

    @Test
    fun `should handle opprettSøknadsbehandling`() {
        val søknad = sak.nySøknad(SøknadInnholdTestdataBuilder.build())
        val behandling = sak.opprettSøknadsbehandling(søknad.toDto().id)
        val sakDto = sak.toDto()

        persistenceObserver.opprettSøknadsbehandlingParams.behandling shouldBeSameInstanceAs behandling
        persistenceObserver.opprettSøknadsbehandlingParams.sakId shouldBe sakDto.id
        sakDto.behandlinger shouldHaveSize 1
        behandling.toDto().søknad.id shouldBe søknad.toDto().id
    }

    class PersistenceObserver : SakPersistenceObserver {
        lateinit var nySøknadParams: NySøknadParams
        override fun nySøknad(sakId: UUID, søknad: Søknad) = søknad.also {
            nySøknadParams = NySøknadParams(sakId, søknad)
        }

        lateinit var opprettSøknadsbehandlingParams: OpprettSøknadsbehandlingParams
        override fun opprettSøknadsbehandling(sakId: UUID, behandling: Behandling) = behandling.also {
            opprettSøknadsbehandlingParams = OpprettSøknadsbehandlingParams(sakId, behandling)
            // TODO Possible to avoid?
            it.addObserver(object : BehandlingPersistenceObserver {
                override fun opprettVilkårsvurderinger(
                    behandlingId: UUID,
                    vilkårsvurderinger: List<Vilkårsvurdering>
                ): List<Vilkårsvurdering> = emptyList()

                override fun opprettBeregning(behandlingId: UUID, beregning: Beregning): Beregning {
                    throw IllegalStateException()
                }

                override fun oppdaterBehandlingStatus(
                    behandlingId: UUID,
                    status: Behandling.BehandlingsStatus
                ): Behandling.BehandlingsStatus {
                    throw NotImplementedError()
                }

                override fun hentOppdrag(sakId: UUID): Oppdrag {
                    return Oppdrag(sakId = sakId)
                }

                override fun hentFnr(sakId: UUID): Fnr {
                    return Fnr("12345678910")
                }

                override fun attester(behandlingId: UUID, attestant: Attestant): Attestant {
                    return attestant
                }
            })
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
        val sakId = UUID.randomUUID()
        sak = Sak(id = sakId, fnr = Fnr("12345678910"), oppdrag = Oppdrag(sakId = sakId)).also {
            it.addObserver(persistenceObserver)
            it.addObserver(eventObserver)
        }
    }
}
