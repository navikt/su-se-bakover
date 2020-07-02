package no.nav.su.se.bakover.domain

import no.nav.su.se.bakover.domain.dto.DtoConvertable

class Stønadsperiode(
    id: Long,
    private val søknad: Søknad,
    private val behandlinger: MutableList<Behandling> = mutableListOf()
) : PersistentDomainObject<StønadsperiodePersistenceObserver>(id), DtoConvertable<StønadsperiodeDto> {

    override fun toDto() = StønadsperiodeDto(
        id = id,
        søknad = søknad.toDto(),
        behandlinger = behandlinger.map { it.toDto() }
    )

    fun nyBehandling(): Behandling {
        val behandling = persistenceObserver.nyBehandling(id)
        behandlinger.add(behandling)
        behandling.opprettVilkårsvurderinger()
        return behandling
    }

    fun nySøknadEvent(sakId: Long) = søknad.nySøknadEvent(sakId)
}

interface StønadsperiodePersistenceObserver : PersistenceObserver {
    fun nyBehandling(stønadsperiodeId: Long): Behandling
}

data class StønadsperiodeDto(
    val id: Long,
    val søknad: SøknadDto,
    val behandlinger: List<BehandlingDto> = emptyList()
)
