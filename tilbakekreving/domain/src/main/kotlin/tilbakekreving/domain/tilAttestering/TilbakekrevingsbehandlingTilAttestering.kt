package tilbakekreving.domain.tilAttestering

import tilbakekreving.domain.Tilbakekrevingsbehandling
import tilbakekreving.domain.vurdert.VurdertTilbakekrevingsbehandling

data class TilbakekrevingsbehandlingTilAttestering(
    val forrigeSteg: VurdertTilbakekrevingsbehandling.Utfylt,
) : Tilbakekrevingsbehandling by forrigeSteg
