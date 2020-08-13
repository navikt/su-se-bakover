package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.dto.DtoConvertable
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import no.nav.su.se.bakover.domain.oppdrag.OppdragDto
import no.nav.su.se.bakover.domain.oppdrag.OppdragFactory
import no.nav.su.se.bakover.domain.oppdrag.Simulering
import java.time.Instant
import java.util.UUID

class Sak(
    id: UUID = UUID.randomUUID(),
    opprettet: Instant = Instant.now(),
    private val fnr: Fnr,
    private val søknader: MutableList<Søknad> = mutableListOf(),
    private val behandlinger: MutableList<Behandling> = mutableListOf(),
    private val oppdrag: MutableList<Oppdrag> = mutableListOf()
) : PersistentDomainObject<SakPersistenceObserver>(id, opprettet), DtoConvertable<SakDto> {
    private val observers: MutableList<SakObserver> = mutableListOf()
    fun addObserver(observer: SakObserver) = observers.add(observer)

    override fun toDto() = SakDto(
        id,
        fnr = fnr,
        søknader = søknader.map { it.toDto() },
        behandlinger = behandlinger.map { it.toDto() },
        opprettet = opprettet,
        oppdrag = oppdrag.map { it.toDto() }
    )

    fun nySøknad(søknadInnhold: SøknadInnhold): Søknad {
        val søknad = persistenceObserver.nySøknad(id, søknad = Søknad(søknadInnhold = søknadInnhold))
        søknader.add(søknad)
        val søknadDto = søknad.toDto()
        observers.filterIsInstance(SakEventObserver::class.java).forEach {
            it.nySøknadEvent(
                SakEventObserver.NySøknadEvent(
                    sakId = id,
                    søknadId = søknadDto.id,
                    søknadInnhold = søknadDto.søknadInnhold
                )
            )
        }
        return søknad
    }

    fun opprettSøknadsbehandling(søknadId: UUID): Behandling {
        val søknad = søknader.single { it.toDto().id == søknadId }
        val behandling = persistenceObserver.opprettSøknadsbehandling(id, Behandling(søknad = søknad))
        behandling.opprettVilkårsvurderinger()
        behandlinger.add(behandling)
        return behandling
    }

    // TODO move OTHER interfaces to domain packages
    interface OppdragClient {
        fun simuler(oppdrag: Oppdrag): Simulering
    }

    fun fullførBehandling(behandlingId: UUID, oppdragClient: OppdragClient): Behandling {
        val behandling = behandlinger.find { it.toDto().id == behandlingId }!!
        val oppdrag = opprettOppdrag(behandling)
        val simulering = oppdragClient.simuler(oppdrag)
        oppdrag.addSimulering(simulering)
        this.oppdrag.add(oppdrag)
        behandling.addOppdrag(oppdrag)
        return behandling
    }

    private fun opprettOppdrag(behandling: Behandling): Oppdrag {
        return persistenceObserver.opprettOppdrag(
            OppdragFactory(
                behandling = behandling.genererOppdragsinformasjon(),
                sak = genererOppdragsinformasjon()
            ).build()
        )
    }

    internal data class Oppdragsinformasjon(
        val sakId: UUID
    )

    internal fun genererOppdragsinformasjon() = Oppdragsinformasjon(sakId = id)
}

interface SakObserver

interface SakPersistenceObserver : PersistenceObserver {
    fun nySøknad(sakId: UUID, søknad: Søknad): Søknad
    fun opprettSøknadsbehandling(
        sakId: UUID,
        behandling: Behandling
    ): Behandling

    fun opprettOppdrag(oppdrag: Oppdrag): Oppdrag
}

interface SakEventObserver : SakObserver {
    fun nySøknadEvent(nySøknadEvent: NySøknadEvent) {}

    data class NySøknadEvent(
        val sakId: UUID,
        val søknadId: UUID,
        val søknadInnhold: SøknadInnhold
    )
}

data class SakDto(
    val id: UUID,
    val fnr: Fnr,
    val søknader: List<SøknadDto> = emptyList(),
    val behandlinger: List<BehandlingDto> = emptyList(),
    val opprettet: Instant,
    val oppdrag: List<OppdragDto> = emptyList()
)
