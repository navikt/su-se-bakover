package tilbakekreving.presentation.api.common

import common.presentation.attestering.AttesteringJson
import common.presentation.attestering.AttesteringJson.Companion.toJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import tilbakekreving.domain.AvbruttTilbakekrevingsbehandling
import tilbakekreving.domain.IverksattTilbakekrevingsbehandling
import tilbakekreving.domain.OpprettetTilbakekrevingsbehandling
import tilbakekreving.domain.Tilbakekrevingsbehandling
import tilbakekreving.domain.TilbakekrevingsbehandlingTilAttestering
import tilbakekreving.domain.UnderBehandling
import tilbakekreving.presentation.api.common.ForhåndsvarselMetaInfoJson.Companion.toJson
import tilbakekreving.presentation.api.common.KravgrunnlagJson.Companion.toJson

data class TilbakekrevingsbehandlingJson(
    val id: String,
    val sakId: String,
    val opprettet: Tidspunkt,
    val opprettetAv: String,
    val kravgrunnlag: KravgrunnlagJson,
    val status: TilbakekrevingsbehandlingStatus,
    val vurderinger: VurderingerMedKravJson?,
    val fritekst: String?,
    val forhåndsvarselsInfo: List<ForhåndsvarselMetaInfoJson>,
    val versjon: Long,
    val sendtTilAttesteringAv: String?,
    val attesteringer: List<AttesteringJson>,
    val erKravgrunnlagUtdatert: Boolean,
    val avsluttetTidspunkt: Tidspunkt?,
    val notat: String?,
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
                is AvbruttTilbakekrevingsbehandling -> TilbakekrevingsbehandlingStatus.AVBRUTT
            },
            vurderinger = this.vurderingerMedKrav?.toJson(),
            forhåndsvarselsInfo = forhåndsvarselsInfo.toJson(),
            fritekst = this.vedtaksbrevvalg?.fritekst,
            versjon = this.versjon.value,
            sendtTilAttesteringAv = when (this) {
                is OpprettetTilbakekrevingsbehandling,
                is AvbruttTilbakekrevingsbehandling,
                is UnderBehandling,
                -> null

                is TilbakekrevingsbehandlingTilAttestering -> this.sendtTilAttesteringAv.toString()
                is IverksattTilbakekrevingsbehandling -> this.forrigeSteg.sendtTilAttesteringAv.toString()
            },
            attesteringer = this.attesteringer.toJson(),
            erKravgrunnlagUtdatert = this.erKravgrunnlagUtdatert,
            avsluttetTidspunkt = (this as? AvbruttTilbakekrevingsbehandling)?.avsluttetTidspunkt,
            notat = this.notat?.value,
        )
    }
}
