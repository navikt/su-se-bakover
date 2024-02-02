package tilbakekreving.domain

import no.nav.su.se.bakover.behandling.BehandlingsId
import java.util.UUID

data class TilbakekrevingsbehandlingId(
    override val value: UUID,
) : BehandlingsId {
    override fun toString(): String = value.toString()

    companion object {
        fun generer() = TilbakekrevingsbehandlingId(UUID.randomUUID())
        fun fraString(id: String) = TilbakekrevingsbehandlingId(UUID.fromString(id))
    }
}
