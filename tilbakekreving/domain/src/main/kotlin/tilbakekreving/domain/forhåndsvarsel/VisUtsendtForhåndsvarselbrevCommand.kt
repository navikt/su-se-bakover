package tilbakekreving.domain.forhåndsvarsel

import tilbakekreving.domain.TilbakekrevingsbehandlingId
import java.util.UUID

data class VisUtsendtForhåndsvarselbrevCommand(
    val sakId: UUID,
    val tilbakekrevingsbehandlingId: TilbakekrevingsbehandlingId,
    val dokumentId: UUID,
)
