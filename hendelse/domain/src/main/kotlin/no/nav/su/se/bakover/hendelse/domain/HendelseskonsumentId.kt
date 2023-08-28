package no.nav.su.se.bakover.hendelse.domain

/**
 * Kan ikke være value class pga. mockito
 */
data class HendelseskonsumentId(val value: String) {
    override fun toString(): String = value
}
