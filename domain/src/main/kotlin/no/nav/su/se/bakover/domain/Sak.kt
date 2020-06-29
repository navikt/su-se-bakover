package no.nav.su.se.bakover.domain

import no.nav.su.meldinger.kafka.soknad.SøknadInnhold

class Sak(
    id: Long,
    private val fnr: Fnr,
    private val stønadsperioder: MutableList<Stønadsperiode> = mutableListOf()
) : PersistentDomainObject<SakPersistenceObserver>(id) {
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
        stønadsperioder.add(persistenceObserver.nySøknad(id, søknadInnhold))

        val event = sisteStønadsperiode().nySøknadEvent(id)
        observers.filterIsInstance(SakEventObserver::class.java).forEach {
            it.nySøknadEvent(event)
        }
    }

    fun sisteStønadsperiode() = stønadsperioder.last()
}

interface SakObserver

interface SakPersistenceObserver : PersistenceObserver {
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
