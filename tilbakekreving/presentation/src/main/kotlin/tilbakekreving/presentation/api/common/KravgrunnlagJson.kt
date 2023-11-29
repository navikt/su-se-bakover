package tilbakekreving.presentation.api.common

import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.serialize
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlagstatus
import tilbakekreving.presentation.api.common.GrunnlagsperiodeJson.Companion.toJson
import tilbakekreving.presentation.api.common.KravgrunnlagStatusJson.Companion.toJson

/**
 * Kontrakten mot su-se-framover
 */
data class KravgrunnlagJson(
    val hendelseId: String,
    val eksternKravgrunnlagsId: String,
    val eksternVedtakId: String,
    val kontrollfelt: String,
    val status: KravgrunnlagStatusJson,
    val grunnlagsperiode: List<GrunnlagsperiodeJson>,
    val summertBetaltSkattForYtelsesgruppen: String,
    val summertBruttoTidligereUtbetalt: Int,
    val summertBruttoNyUtbetaling: Int,
    val summertBruttoFeilutbetaling: Int,
    val summertNettoFeilutbetaling: Int,
    val summertSkattFeilutbetaling: Int,
) {

    companion object {
        fun Kravgrunnlag.toJson(): KravgrunnlagJson = KravgrunnlagJson(
            eksternKravgrunnlagsId = this.eksternKravgrunnlagId,
            eksternVedtakId = this.eksternVedtakId,
            kontrollfelt = this.eksternKontrollfelt,
            status = this.status.toJson(),
            grunnlagsperiode = this.grunnlagsperioder.toJson(),
            hendelseId = this.hendelseId.toString(),
            summertBetaltSkattForYtelsesgruppen = this.summertBetaltSkattForYtelsesgruppen.toString(),
            summertBruttoTidligereUtbetalt = this.summertBruttoTidligereUtbetalt,
            summertBruttoNyUtbetaling = this.summertBruttoNyUtbetaling,
            summertBruttoFeilutbetaling = this.summertBruttoFeilutbetaling,
            summertNettoFeilutbetaling = this.summertNettoFeilutbetaling,
            summertSkattFeilutbetaling = this.summertSkattFeilutbetaling,
        )

        fun Kravgrunnlag.toStringifiedJson(): String = serialize(this.toJson())
    }
}

data class GrunnlagsperiodeJson(
    val periode: PeriodeJson,
    val betaltSkattForYtelsesgruppen: String,
    val bruttoTidligereUtbetalt: String,
    val bruttoNyUtbetaling: String,
    val bruttoFeilutbetaling: String,
    val nettoFeilutbetaling: String,
    val skatteProsent: String,
    val skattFeilutbetaling: String,
) {
    companion object {
        fun List<Kravgrunnlag.Grunnlagsperiode>.toJson(): List<GrunnlagsperiodeJson> = this.map {
            GrunnlagsperiodeJson(
                periode = it.periode.toJson(),
                betaltSkattForYtelsesgruppen = it.betaltSkattForYtelsesgruppen.toString(),
                bruttoTidligereUtbetalt = it.bruttoTidligereUtbetalt.toString(),
                bruttoNyUtbetaling = it.bruttoNyUtbetaling.toString(),
                bruttoFeilutbetaling = it.bruttoFeilutbetaling.toString(),
                nettoFeilutbetaling = it.nettoFeilutbetaling.toString(),
                skatteProsent = it.skatteProsent.toString(),
                skattFeilutbetaling = it.skattFeilutbetaling.toString(),
            )
        }
    }
}

enum class KravgrunnlagStatusJson {
    ANNU,
    ANOM,
    AVSL,
    BEHA,
    ENDR,
    FEIL,
    MANU,
    NY,
    SPER,
    ;

    companion object {
        fun Kravgrunnlagstatus.toJson(): KravgrunnlagStatusJson = when (this) {
            Kravgrunnlagstatus.Annulert -> ANNU
            Kravgrunnlagstatus.AnnulertVedOmg -> ANOM
            Kravgrunnlagstatus.Avsluttet -> AVSL
            Kravgrunnlagstatus.Ferdigbehandlet -> BEHA
            Kravgrunnlagstatus.Endret -> ENDR
            Kravgrunnlagstatus.Feil -> FEIL
            Kravgrunnlagstatus.Manuell -> MANU
            Kravgrunnlagstatus.Nytt -> NY
            Kravgrunnlagstatus.Sperret -> SPER
        }
    }
}
