package no.nav.su.se.bakover.domain.hendelseslogg

import no.nav.su.se.bakover.domain.PersistenceObserver
import no.nav.su.se.bakover.domain.PersistentDomainObject
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.Hendelse

/**
 * Generisk hendelseslogg som kan knyttes til et objekt vha. id feltet.
 * Dvs objekt-id = hendelseslogg-id. Oppslag i hendeleslogg-tabell gjøres på denne id-en.
 */
data class Hendelseslogg(
    val id: String,
    private var hendelser: MutableList<Hendelse> = mutableListOf()
) : PersistentDomainObject<HendelsesloggPersistenceObserver>() {
    fun hendelser() = hendelser.toList()
    fun hendelse(hendelse: Hendelse): Hendelseslogg {
        hendelser.add(hendelse)
        return persistenceObserver.oppdaterHendelseslogg(this)
    }
}

interface HendelsesloggPersistenceObserver : PersistenceObserver {
    fun oppdaterHendelseslogg(hendelseslogg: Hendelseslogg): Hendelseslogg
}
