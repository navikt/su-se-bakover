package no.nav.su.se.bakover.hendelse.domain

/**
 * Implementeres av de som skal lytte på hendelser.
 *
 * @property konsumentId En unik id for denne subscriberen.
 */
interface Hendelseskonsument {
    val konsumentId: HendelseskonsumentId
}
