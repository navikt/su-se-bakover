package no.nav.su.se.bakover.hendelse.domain

/**
 * Implementeres av de som skal lytte på hendelser.
 *
 * @property subscriberId En unik id for denne subscriberen.
 */
interface HendelseSubscriber {
    val subscriberId: String
}
