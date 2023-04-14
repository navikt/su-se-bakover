package no.nav.su.se.bakover.domain.skatt

import java.util.UUID

data class Skattereferanser(
    val søkers: UUID,
    val eps: UUID?,
) {
    fun harEPS(): Boolean = eps != null
    fun fjernEps(): Skattereferanser = this.copy(eps = null)
}
