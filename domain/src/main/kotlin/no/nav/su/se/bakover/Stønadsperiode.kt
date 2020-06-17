package no.nav.su.se.bakover


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

    fun nyBehandling() = observers.filterIsInstance(StønadsperiodePersistenceObserver::class.java).forEach {
        behandlinger.add(it.nyBehandling(id))
    }

    fun nySøknadEvent(sakId: Long) = søknad.nySøknadEvent(sakId)
}

interface StønadsperiodeObserver

interface StønadsperiodePersistenceObserver : StønadsperiodeObserver {
    fun nyBehandling(stønadsperiodeId: Long): Behandling
}