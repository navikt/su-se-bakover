package no.nav.su.se.bakover

class Behandling internal  constructor(
        private val id: Long
) : Persistent {

    override fun id() = id

}

class BehandlingFactory(
        private val behandlingRepo: BehandlingRepo
) {
    fun opprett(stønadsperiodeId: Long) : Behandling {
        val behandlingId = behandlingRepo.nyBehandling(stønadsperiodeId)
        return Behandling(behandlingId)
    }
}

interface BehandlingRepo {
    fun nyBehandling(stønadsperiodeId: Long) : Long
    fun hentBehandling(id: Long) : Long
}