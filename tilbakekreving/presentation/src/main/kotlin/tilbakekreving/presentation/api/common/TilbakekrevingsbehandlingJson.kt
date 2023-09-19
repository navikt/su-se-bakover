package tilbakekreving.presentation.api.common

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import tilbakekreving.domain.ManuellTilbakekrevingsbehandling
import tilbakekreving.presentation.api.common.KravgrunnlagJson.Companion.toJson
import java.util.UUID

data class TilbakekrevingsbehandlingJson(
    val id: UUID,
    val sakId: UUID,
    val opprettet: Tidspunkt,
    val kravgrunnlag: KravgrunnlagJson,
) {

    companion object {
        fun ManuellTilbakekrevingsbehandling.toJson(): String = serialize(
            TilbakekrevingsbehandlingJson(
                id = id,
                sakId = sakId,
                opprettet = opprettet,
                kravgrunnlag = this.kravgrunnlag.toJson(),
            ),
        )
    }
}
