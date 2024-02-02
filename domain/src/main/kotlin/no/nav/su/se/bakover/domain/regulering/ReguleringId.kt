package no.nav.su.se.bakover.domain.regulering

import no.nav.su.se.bakover.common.domain.BehandlingsId
import java.util.UUID

/**
 * TODO - burde flyttes til behandlingsmodulen når det lar seg gjøre
 */
data class ReguleringId(
    override val value: UUID,
) : BehandlingsId {
    override fun toString(): String = value.toString()

    companion object {
        fun generer() = ReguleringId(UUID.randomUUID())
        fun fraString(id: String) = ReguleringId(UUID.fromString(id))
    }
}
