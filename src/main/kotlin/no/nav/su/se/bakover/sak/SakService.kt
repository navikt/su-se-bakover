package no.nav.su.se.bakover.sak

import com.google.gson.JsonObject
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad

class SakService(
    private val sakRepository: SakRepository
) {

    fun lagreSøknad(fnr: String, søknad: JsonObject): Long? {
        return finnSak(fnr).lagreSøknad(søknad)
    }

    fun opprettSak(fnr: String): Long {
        return sakRepository.opprettSak(fnr)
    }

    private fun finnSak(fnr: String): Sak {
        return sakRepository.hentSak(fnr) ?: sakRepository.opprettSak(fnr).let {
            sakRepository.hentSak(it)!! // Her bør den saken vi nettopp opprettet eksistere...
        }
    }

    private fun Sak.lagreSøknad(søknad: JsonObject): Long? {
        return sakRepository.lagreSøknad(sakId = this.id, søknadJson = søknad)
    }

    fun hentSoknadForPerson(personIdent: String): Søknad? {
        return sakRepository.hentSoknadForPerson(personIdent)
    }

    fun hentSøknad(søknadIdAsLong: Long): Søknad? {
        return sakRepository.hentSøknad(søknadIdAsLong)
    }

    fun hentSak(fnr: String): Sak? {
        return sakRepository.hentSak(fnr)
    }

    fun hentSak(sakId: Long): Sak? {
        return sakRepository.hentSak(sakId)
    }

    fun hentSøknaderForSak(sakId: Long): List<Søknad> {
        return sakRepository.hentSøknaderForSak(sakId = sakId)
    }

    fun hentAlleSaker(): List<Sak> {
        return sakRepository.hentAlleSaker()
    }

}