package no.nav.su.se.bakover.sak

import com.google.gson.JsonObject
import no.nav.su.se.bakover.domain.Sak

internal class SakService(
    private val postgresRepository: PostgresRepository
): Repository by postgresRepository {

    fun lagreSøknad(fnr: String, søknad: JsonObject): Long? {
        return finnSak(fnr).lagreSøknad(søknad)
    }

    private fun finnSak(fnr: String): Sak {
        return postgresRepository.hentSak(fnr) ?: postgresRepository.opprettSak(fnr).let {
            postgresRepository.hentSak(it)!! // Her bør den saken vi nettopp opprettet eksistere...
        }
    }

    private fun Sak.lagreSøknad(søknad: JsonObject): Long? {
        return postgresRepository.lagreSøknad(sakId = this.id, søknadJson = søknad)
    }

}