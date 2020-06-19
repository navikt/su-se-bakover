package no.nav.su.se.bakover.domain

import no.nav.su.meldinger.kafka.soknad.SøknadInnhold

private const val NO_SUCH_IDENTITY = Long.MIN_VALUE

class Sak constructor(
        private val fnr: Fnr,
        private val id: Long = NO_SUCH_IDENTITY,
        private val stønadsperioder: MutableList<Stønadsperiode> = mutableListOf()
) {
    private val observers: MutableList<SakObserver> = mutableListOf()
    fun addObserver(observer: SakObserver) = observers.add(observer)

    fun toJson() = """
        {
            "id": $id,
            "fnr":"$fnr",
            "stønadsperioder": ${stønadsperioderSomJsonListe()}
        }
    """.trimIndent()

    private fun stønadsperioderSomJsonListe(): String = "[ ${stønadsperioder.joinToString(",") { it.toJson() }} ]"

    fun nySøknad(søknadInnhold: SøknadInnhold) {
        observers.filterIsInstance(SakPersistenceObserver::class.java).forEach {
            stønadsperioder.add(it.nySøknad(id, søknadInnhold))
        }
        val event = sisteStønadsperiode().nySøknadEvent(id)
        observers.filterIsInstance(SakEventObserver::class.java).forEach {
            it.nySøknadEvent(event)
        }
    }

    fun sisteStønadsperiode() = stønadsperioder.last()
}

interface SakObserver

interface SakPersistenceObserver : SakObserver {
    fun nySøknad(sakId: Long, søknadInnhold: SøknadInnhold): Stønadsperiode
}

interface SakEventObserver : SakObserver {
    fun nySøknadEvent(nySøknadEvent: NySøknadEvent) {}

    data class NySøknadEvent(
            val sakId: Long,
            val søknadId: Long,
            val søknadInnhold: SøknadInnhold
    )
}