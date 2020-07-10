package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.dto.DtoConvertable
import java.time.Instant
import java.util.UUID

class Sak(
    id: UUID = UUID.randomUUID(),
    opprettet: Instant = Instant.now(),
    private val fnr: Fnr,
    private val søknader: MutableList<Søknad> = mutableListOf(),
    private val behandlinger: MutableList<Behandling> = mutableListOf()
) : PersistentDomainObject<SakPersistenceObserver>(id, opprettet), DtoConvertable<SakDto> {
    private val observers: MutableList<SakObserver> = mutableListOf()
    fun addObserver(observer: SakObserver) = observers.add(observer)

    override fun toDto() = SakDto(
        id,
        fnr = fnr,
        søknader = søknader.map { it.toDto() },
        behandlinger = behandlinger.map { it.toDto() },
        opprettet = opprettet
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
}

interface SakObserver

interface SakPersistenceObserver : PersistenceObserver {
    fun nySøknad(sakId: UUID, søknad: Søknad): Søknad
    fun opprettSøknadsbehandling(
        sakId: UUID,
        behandling: Behandling
    ): Behandling
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
    val opprettet: Instant
)
