package no.nav.su.se.bakover.domain

private const val NO_SUCH_IDENTITY = Long.MIN_VALUE

class Stønadsperiode(
    private val id: Long = NO_SUCH_IDENTITY,
    private val søknad: Søknad,
    private val behandlinger: MutableList<Behandling> = mutableListOf()
) {
    private val observers: MutableList<StønadsperiodeObserver> = mutableListOf()
    fun addObserver(observer: StønadsperiodeObserver) = observers.add(observer)

    fun toJson() = """
        {
            "id":$id,
            "søknad": ${søknad.toJson()},
            "behandlinger": ${behandlingerAsJson()}
        }
    """.trimIndent()

    private fun behandlingerAsJson(): String = "[ ${behandlinger.joinToString(",") { it.toJson() }} ]"

    fun nyBehandling(): Behandling {
        lateinit var behandling: Behandling
        observers.filterIsInstance(StønadsperiodePersistenceObserver::class.java).forEach {
            behandling = it.nyBehandling(id)
            behandlinger.add(behandling)
        }
        return behandling
    }

    fun nySøknadEvent(sakId: Long) = søknad.nySøknadEvent(sakId)
}

interface StønadsperiodeObserver

interface StønadsperiodePersistenceObserver : StønadsperiodeObserver {
    fun nyBehandling(stønadsperiodeId: Long): Behandling
}
