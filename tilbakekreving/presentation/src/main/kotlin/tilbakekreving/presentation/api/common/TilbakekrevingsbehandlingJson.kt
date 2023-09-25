package tilbakekreving.presentation.api.common

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import tilbakekreving.domain.Tilbakekrevingsbehandling
import tilbakekreving.presentation.api.common.KravgrunnlagJson.Companion.toJson

data class TilbakekrevingsbehandlingJson(
    val id: String,
    val sakId: String,
    val opprettet: Tidspunkt,
    val opprettetAv: String,
    val kravgrunnlag: KravgrunnlagJson,
) {

    companion object {
        fun Tilbakekrevingsbehandling.toStringifiedJson(): String = serialize(this.toJson())
        fun List<Tilbakekrevingsbehandling>.toJson(): List<TilbakekrevingsbehandlingJson> = this.map { it.toJson() }

        fun Tilbakekrevingsbehandling.toJson(): TilbakekrevingsbehandlingJson = TilbakekrevingsbehandlingJson(
            id = id.toString(),
            sakId = sakId.toString(),
            opprettet = opprettet,
            opprettetAv = opprettetAv.toString(),
            kravgrunnlag = this.kravgrunnlag.toJson(),
        )
    }
}
