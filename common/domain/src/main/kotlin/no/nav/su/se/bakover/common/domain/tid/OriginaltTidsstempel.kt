package no.nav.su.se.bakover.common.tid

/**
 * Represents the original timestamp from when the object was first known in the system.
 * Used to preserve temporal ordering.
 */
interface OriginaltTidsstempel {
    val opprettet: Tidspunkt
}
