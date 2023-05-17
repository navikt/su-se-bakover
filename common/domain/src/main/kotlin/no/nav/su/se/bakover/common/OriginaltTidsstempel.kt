package no.nav.su.se.bakover.common

/**
 * Represents the original timestamp from when the object was first known in the system.
 * Used to preserve temporal ordering.
 */
interface OriginaltTidsstempel {
    val opprettet: Tidspunkt
}
