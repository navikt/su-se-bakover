package no.nav.su.se.bakover.domain.søknadsbehandling

import no.nav.su.se.bakover.behandling.BehandlingsId
import java.util.UUID

/**
 * TODO - burde flyttes til behandlingsmodulen når det lar seg gjøre
 */
data class SøknadsbehandlingId(
    override val value: UUID,
) : BehandlingsId {
    override fun toString(): String = value.toString()

    companion object {
        fun generer(): SøknadsbehandlingId = SøknadsbehandlingId(UUID.randomUUID())
        fun fraString(id: String): SøknadsbehandlingId = SøknadsbehandlingId(UUID.fromString(id))
    }
}
