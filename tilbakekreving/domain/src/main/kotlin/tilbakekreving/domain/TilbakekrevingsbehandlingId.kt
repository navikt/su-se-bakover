package tilbakekreving.domain

import java.util.UUID

data class TilbakekrevingsbehandlingId(
    val value: UUID,
) {
    override fun toString(): String = value.toString()

    companion object {
        fun generer() = TilbakekrevingsbehandlingId(UUID.randomUUID())
        fun fraString(id: String) = TilbakekrevingsbehandlingId(UUID.fromString(id))
    }
}
