package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.common.domain.BehandlingsId
import java.util.UUID

/**
 * TODO - burde flyttes til behandlingsmodulen når det lar seg gjøre
 */
data class RevurderingId(
    override val value: UUID,
) : BehandlingsId {
    override fun toString(): String = value.toString()

    companion object {
        fun generer() = RevurderingId(UUID.randomUUID())
        fun fraString(id: String) = RevurderingId(UUID.fromString(id))
    }
}
