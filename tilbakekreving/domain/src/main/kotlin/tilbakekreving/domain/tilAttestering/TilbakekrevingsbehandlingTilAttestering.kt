@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

data class TilbakekrevingsbehandlingTilAttestering(
    val forrigeSteg: VurdertTilbakekrevingsbehandling.Utfylt,
) : Tilbakekrevingsbehandling by forrigeSteg
