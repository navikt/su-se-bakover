package no.nav.su.se.bakover.domain

class Stønadsperiode(
    id: Long,
    private val søknad: Søknad,
    private val behandlinger: MutableList<Behandling> = mutableListOf()
) : PersistentDomainObject<StønadsperiodePersistenceObserver>(id) {

    fun toJson() = """
        {
            "id":$id,
            "søknad": ${søknad.toJson()},
            "behandlinger": ${behandlingerAsJson()}
        }
    """.trimIndent()

    private fun behandlingerAsJson(): String = "[ ${behandlinger.joinToString(",") { it.toJson() }} ]"

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
