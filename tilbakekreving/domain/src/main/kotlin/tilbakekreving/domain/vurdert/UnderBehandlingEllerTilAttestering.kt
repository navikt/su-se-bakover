@file:Suppress("PackageDirectoryMismatch")
// Må ligge i samme pakke som Tilbakekrevingsbehandling (siden det er et sealed interface), men trenger ikke ligge i samme mappe.

package tilbakekreving.domain

/**
 * forrigeSteg til [UnderBehandling.Utfylt] kan være en av disse 3: [UnderBehandling.Påbegynt], [UnderBehandling.Utfylt] og [TilbakekrevingsbehandlingTilAttestering]
 */
sealed interface UnderBehandlingEllerTilAttestering : Tilbakekrevingsbehandling
