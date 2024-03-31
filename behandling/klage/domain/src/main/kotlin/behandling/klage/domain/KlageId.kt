package behandling.klage.domain

import no.nav.su.se.bakover.common.domain.BehandlingsId
import java.util.UUID

/**
 * TODO - burde flyttes til behandlingsmodulen når det lar seg gjøre
 */
data class KlageId(
    override val value: UUID,
) : BehandlingsId {
    override fun toString(): String = value.toString()

    companion object {
        fun generer() = KlageId(UUID.randomUUID())
        fun fraString(id: String) = KlageId(UUID.fromString(id))
    }
}
