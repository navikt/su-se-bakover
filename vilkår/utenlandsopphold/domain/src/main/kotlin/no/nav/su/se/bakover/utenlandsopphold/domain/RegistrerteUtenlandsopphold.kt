package no.nav.su.se.bakover.utenlandsopphold.domain

import java.util.UUID

/**
 * Inneholder logikk/regler for utenlandsopphold.
 */
data class RegistrerteUtenlandsopphold(
    private val sakId: UUID,
    private val utenlandsopphold: List<RegistrertUtenlandsopphold>,
) : List<RegistrertUtenlandsopphold> by utenlandsopphold {
    init {
        this.map { it.versjon }.let {
            require(it.sorted() == it) {
                "Utenlandsopphold for sak $sakId må være sortert etter versjon, men var: $it"
            }
            require(it.distinct() == it) {
                "Utenlandsopphold for sak $sakId kan ikke inneholde duplikater: $it"
            }
        }
    }

    companion object {
        fun empty(sakId: UUID) = RegistrerteUtenlandsopphold(sakId, listOf())
    }

    val antallDager by lazy {
        this.filterNot { it.erAnnullert }.sumOf { it.antallDager }
    }
}
