package tilbakekreving.domain

/**
 * forrigeSteg til [UnderBehandling.Utfylt] kan være en av disse 3: [UnderBehandling.Påbegynt], [UnderBehandling.Utfylt] og [TilbakekrevingsbehandlingTilAttestering]
 *
 * Kan sees på en union-type av [UnderBehandling] og [TilbakekrevingsbehandlingTilAttestering]
 */
sealed interface UnderBehandlingEllerTilAttestering : Tilbakekrevingsbehandling
