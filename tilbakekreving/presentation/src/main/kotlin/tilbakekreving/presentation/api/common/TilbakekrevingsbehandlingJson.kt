package tilbakekreving.presentation.api.common

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import tilbakekreving.domain.IverksattTilbakekrevingsbehandling
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandling
import tilbakekreving.domain.Tilbakekrevingsbehandling
import tilbakekreving.domain.TilbakekrevingsbehandlingTilAttestering
import tilbakekreving.domain.UnderBehandling
import tilbakekreving.presentation.api.common.KravgrunnlagJson.Companion.toJson
import java.util.UUID

data class TilbakekrevingsbehandlingJson(
    val id: String,
    val sakId: String,
    val opprettet: Tidspunkt,
    val opprettetAv: String,
    val kravgrunnlag: KravgrunnlagJson,
    val status: TilbakekrevingsbehandlingStatus,
    val månedsvurderinger: List<MånedsvurderingJson>,
    val fritekst: String?,
    val forhåndsvarselDokumenter: List<UUID>,
    val versjon: Long,
    val sendtTilAttesteringAv: String?,
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
            status = when (this) {
                is OpprettetTilbakekrevingsbehandling -> TilbakekrevingsbehandlingStatus.OPPRETTET
                is UnderBehandling.Påbegynt -> {
                    when {
                        this.erVurdert() -> TilbakekrevingsbehandlingStatus.VURDERT
                        else -> TilbakekrevingsbehandlingStatus.FORHÅNDSVARSLET
                    }
                }

                is UnderBehandling.Utfylt -> TilbakekrevingsbehandlingStatus.VEDTAKSBREV
                is TilbakekrevingsbehandlingTilAttestering -> TilbakekrevingsbehandlingStatus.TIL_ATTESTERING
                is IverksattTilbakekrevingsbehandling -> TilbakekrevingsbehandlingStatus.IVERKSATT
                else -> throw IllegalStateException("tilbakekreving $id har ikke en mappet tilstand til frontend")
            },
            månedsvurderinger = this.månedsvurderinger?.vurderinger?.map {
                MånedsvurderingJson(
                    it.måned.toString(),
                    it.vurdering.toString(),
                )
            } ?: emptyList(),
            forhåndsvarselDokumenter = forhåndsvarselDokumentIder,
            fritekst = this.vedtaksbrevvalg?.fritekst,
            versjon = this.versjon.value,
            sendtTilAttesteringAv = when (this) {
                is OpprettetTilbakekrevingsbehandling,
                is UnderBehandling,
                -> null

                is TilbakekrevingsbehandlingTilAttestering -> this.sendtTilAttesteringAv.toString()
                is IverksattTilbakekrevingsbehandling -> this.forrigeSteg.sendtTilAttesteringAv.toString()
                else -> throw IllegalStateException("tilbakekreving $id har ikke en mappet tilstand til frontend for sendTilAttesteringAv")
            },
        )
    }
}
