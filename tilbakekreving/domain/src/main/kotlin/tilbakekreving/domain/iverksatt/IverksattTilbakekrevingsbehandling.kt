package tilbakekreving.domain.iverksatt

import tilbakekreving.domain.Tilbakekrevingsbehandling
import tilbakekreving.domain.tilAttestering.TilbakekrevingsbehandlingTilAttestering

data class IverksattTilbakekrevingsbehandling(
    val forrigeSteg: TilbakekrevingsbehandlingTilAttestering,
) : Tilbakekrevingsbehandling by forrigeSteg
