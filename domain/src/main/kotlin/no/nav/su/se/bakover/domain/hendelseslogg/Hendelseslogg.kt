package no.nav.su.se.bakover.domain.hendelseslogg

import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.Hendelse

/**
 * Generisk hendelseslogg som kan knyttes til et objekt vha. id feltet.
 * Dvs objekt-id = hendelseslogg-id. Oppslag i hendeleslogg-tabell gjøres på denne id-en.
 */
data class Hendelseslogg(
    val id: String,
    private var hendelser: MutableList<Hendelse> = mutableListOf()
) {
    fun hendelser() = hendelser.toList()
    fun hendelse(hendelse: Hendelse): Hendelseslogg {
        hendelser.add(hendelse)
        return this
    }
}
