package no.nav.su.se.bakover.sak

import com.google.gson.JsonObject
import no.nav.su.se.bakover.domain.Sak

internal class SakService(
    private val sakRepository: SakRepository
): SakRepo by sakRepository {

    fun lagreSøknad(fnr: String, søknad: JsonObject): Long? {
        return finnSak(fnr).lagreSøknad(søknad)
    }

    private fun finnSak(fnr: String): Sak {
        return sakRepository.hentSak(fnr) ?: sakRepository.opprettSak(fnr).let {
            sakRepository.hentSak(it)!! // Her bør den saken vi nettopp opprettet eksistere...
        }
    }

    private fun Sak.lagreSøknad(søknad: JsonObject): Long? {
        return sakRepository.lagreSøknad(sakId = this.id, søknadJson = søknad)
    }

}