package no.nav.su.se.bakover.common.domain.statistikk

import no.nav.su.se.bakover.common.person.Fnr

class StatistikkKode6Kontekst(
    val harTilgangTilStrengtFortroligAdresse: Boolean,
) {
    private val personoppslag = mutableMapOf<Fnr, Boolean>()
    private val feiledePersonoppslag = mutableSetOf<Fnr>()

    fun registrerPersonoppslag(fnr: Fnr, harKode6: Boolean) {
        personoppslag[fnr] = harKode6
        feiledePersonoppslag.remove(fnr)
    }

    fun registrerFeiletPersonoppslag(fnr: Fnr) {
        personoppslag.remove(fnr)
        feiledePersonoppslag.add(fnr)
    }

    fun harKode6(søker: Fnr): Boolean {
        check(feiledePersonoppslag.isEmpty()) {
            "Kan ikke lage sakstatistikk når personoppslag har feilet"
        }
        check(personoppslag.containsKey(søker)) {
            "Kan ikke lage sakstatistikk uten kontrollert adressebeskyttelse for søker"
        }
        return personoppslag.values.any { it }
    }
}
