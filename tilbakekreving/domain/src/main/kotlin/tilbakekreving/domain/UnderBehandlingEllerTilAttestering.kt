package tilbakekreving.domain

/**
 * forrigeSteg til [UnderBehandling.MedKravgrunnlag.Utfylt] kan være en av disse 3:
 * [UnderBehandling.MedKravgrunnlag.Påbegynt], [UnderBehandling.MedKravgrunnlag.Utfylt] og [TilbakekrevingsbehandlingTilAttestering]
 *
 * Kan sees på en union-type av [UnderBehandling] og [TilbakekrevingsbehandlingTilAttestering]
 */
sealed interface UnderBehandlingEllerTilAttestering : Tilbakekrevingsbehandling
