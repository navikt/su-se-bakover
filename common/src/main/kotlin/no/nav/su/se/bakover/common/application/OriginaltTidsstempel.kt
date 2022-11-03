package no.nav.su.se.bakover.common.application

import no.nav.su.se.bakover.common.Tidspunkt

/**
 * Represents the original timestamp from when the object was first known in the system.
 * Used to preserve temporal ordering.
 */
interface OriginaltTidsstempel {
    val opprettet: Tidspunkt
}
