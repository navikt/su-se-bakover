package no.nav.su.se.bakover.soknad

import no.nav.su.se.bakover.sak.SakRepository
import org.json.JSONObject

class SøknadService(
        private val søknadRepository: SøknadRepository,
        private val sakRepository: SakRepository
) {
    fun lagreSøknad(søknad: String): Long? {
        val fnr = JSONObject(søknad).getJSONObject("personopplysninger").getString("fnr")
        sakRepository.hentSak(fnr)?.let {
            return søknadRepository.lagreSøknad(søknad, it.id())
        } ?: sakRepository.opprettSak(fnr)?.let {
            return søknadRepository.lagreSøknad(søknad, it)
        } ?: return null
    }
}