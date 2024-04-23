package no.nav.su.se.bakover.domain.regulering.supplement

import no.nav.su.se.bakover.common.person.Fnr

/**
 * Et reguleringssupplement er data som mottas fra en ekstern kilde, for eksempel PESYS, som brukes for å justere
 * på utbetalingen. Denne vil være delt opp i en [ReguleringssupplementFor] per person (kan være både brukere og EPS).
 */
data class Reguleringssupplement(
    private val supplement: List<ReguleringssupplementFor>,
) : List<ReguleringssupplementFor> by supplement {

    fun getFor(fnr: Fnr): ReguleringssupplementFor? = this.supplement.singleOrNull { it.fnr == fnr }

    companion object {
        fun empty() = Reguleringssupplement(emptyList())
    }
}
