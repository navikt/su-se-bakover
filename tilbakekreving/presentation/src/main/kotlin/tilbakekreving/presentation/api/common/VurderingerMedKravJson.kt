package tilbakekreving.presentation.api.common

import tilbakekreving.domain.vurdering.VurderingerMedKrav

data class VurderingerMedKravJson(
    val perioder: List<VurderingMedKravForPeriodeJson>,
    val eksternKravgrunnlagId: String,
    val eksternVedtakId: String,
    val eksternKontrollfelt: String,
    val bruttoSkalTilbakekreveSummert: Int,
    val nettoSkalTilbakekreveSummert: Int,
    val bruttoSkalIkkeTilbakekreveSummert: Int,
    val betaltSkattForYtelsesgruppenSummert: Int,
    val bruttoNyUtbetalingSummert: Int,
    val bruttoTidligereUtbetaltSummert: Int,
)

internal fun VurderingerMedKrav.toJson(): VurderingerMedKravJson {
    return VurderingerMedKravJson(
        eksternKravgrunnlagId = this.eksternKravgrunnlagId,
        eksternVedtakId = this.eksternVedtakId,
        eksternKontrollfelt = this.eksternKontrollfelt,
        bruttoSkalTilbakekreveSummert = this.bruttoSkalTilbakekreveSummert,
        nettoSkalTilbakekreveSummert = this.nettoSkalTilbakekreveSummert,
        bruttoSkalIkkeTilbakekreveSummert = this.bruttoSkalIkkeTilbakekreveSummert,
        perioder = this.perioder.map { it.toJson() },
        betaltSkattForYtelsesgruppenSummert = this.betaltSkattForYtelsesgruppenSummert,
        bruttoNyUtbetalingSummert = this.bruttoNyUtbetalingSummert,
        bruttoTidligereUtbetaltSummert = this.bruttoTidligereUtbetaltSummert,
    )
}
