package tilbakekreving.presentation.api.common

import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.serialize
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.SummertGrunnlagsmåneder
import tilbakekreving.domain.kravgrunnlag.SummertGrunnlagsmåneder.Companion.netto
import tilbakekreving.domain.kravgrunnlag.total
import tilbakekreving.presentation.api.common.GrunnlagsbeløpYtelseJson.Companion.toJson
import tilbakekreving.presentation.api.common.GrunnlagsperiodeJson.Companion.toJson
import tilbakekreving.presentation.api.common.KravgrunnlagStatusJson.Companion.toJson
import tilbakekreving.presentation.api.common.SummertGrunnlagsmånederJson.Companion.toJson
import java.math.BigDecimal

/**
 * Kontrakten mot su-se-framover
 */
data class KravgrunnlagJson(
    val eksternKravgrunnlagsId: String,
    val eksternVedtakId: String,
    val kontrollfelt: String,
    val status: KravgrunnlagStatusJson,
    val grunnlagsperiode: List<GrunnlagsperiodeJson>,
    val summertGrunnlagsmåneder: SummertGrunnlagsmånederJson,
) {

    companion object {
        fun Kravgrunnlag.toJson(): KravgrunnlagJson = KravgrunnlagJson(
            eksternKravgrunnlagsId = this.eksternKravgrunnlagId,
            eksternVedtakId = this.eksternVedtakId,
            kontrollfelt = this.eksternKontrollfelt,
            status = this.status.toJson(),
            grunnlagsperiode = this.grunnlagsmåneder.toJson(),
            summertGrunnlagsmåneder = this.grunnlagsmåneder.total().toJson(),
        )

        fun Kravgrunnlag.toStringifiedJson(): String = serialize(this.toJson())
    }
}

data class GrunnlagsperiodeJson(
    // TODO bytt till et uuuu-MM format (f.eks. toString() av YearMonth)
    val periode: PeriodeJson,
    val beløpSkattMnd: String,
    val ytelse: GrunnlagsbeløpYtelseJson,
) {
    companion object {
        fun List<Kravgrunnlag.Grunnlagsmåned>.toJson(): List<GrunnlagsperiodeJson> = this.map {
            GrunnlagsperiodeJson(
                periode = it.måned.toJson(),
                beløpSkattMnd = it.betaltSkattForYtelsesgruppen.toString(),
                ytelse = it.ytelse.toJson(it.betaltSkattForYtelsesgruppen),
            )
        }
    }
}

data class GrunnlagsbeløpYtelseJson(
    val beløpTidligereUtbetaling: String,
    val beløpNyUtbetaling: String,
    val beløpSkalTilbakekreves: String,
    val beløpSkalIkkeTilbakekreves: String,
    val skatteProsent: String,
    val nettoBeløp: String,
) {
    companion object {
        fun Kravgrunnlag.Grunnlagsmåned.Ytelse.toJson(betaltSkattForYtelsesgruppen: BigDecimal): GrunnlagsbeløpYtelseJson = GrunnlagsbeløpYtelseJson(
            beløpTidligereUtbetaling = this.beløpTidligereUtbetaling.toString(),
            beløpNyUtbetaling = this.beløpNyUtbetaling.toString(),
            beløpSkalTilbakekreves = this.beløpSkalTilbakekreves.toString(),
            beløpSkalIkkeTilbakekreves = this.beløpSkalIkkeTilbakekreves.toString(),
            skatteProsent = this.skatteProsent.toString(),
            nettoBeløp = this.netto(betaltSkattForYtelsesgruppen).sum().toString(),
        )
    }
}

data class SummertGrunnlagsmånederJson(
    val betaltSkattForYtelsesgruppen: String,
    val beløpTidligereUtbetaling: String,
    val beløpNyUtbetaling: String,
    val beløpSkalTilbakekreves: String,
    val beløpSkalIkkeTilbakekreves: String,
    val nettoBeløp: String,
) {
    companion object {
        fun SummertGrunnlagsmåneder.toJson(): SummertGrunnlagsmånederJson = SummertGrunnlagsmånederJson(
            betaltSkattForYtelsesgruppen = this.betaltSkattForYtelsesgruppen.toString(),
            beløpTidligereUtbetaling = this.beløpTidligereUtbetaling.toString(),
            beløpNyUtbetaling = this.beløpNyUtbetaling.toString(),
            beløpSkalTilbakekreves = this.beløpSkalTilbakekreves.toString(),
            beløpSkalIkkeTilbakekreves = this.beløpSkalIkkeTilbakekreves.toString(),
            nettoBeløp = this.nettoBeløp.sum().toString(),
        )
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
        fun Kravgrunnlag.KravgrunnlagStatus.toJson(): KravgrunnlagStatusJson = when (this) {
            Kravgrunnlag.KravgrunnlagStatus.Annulert -> ANNU
            Kravgrunnlag.KravgrunnlagStatus.AnnulertVedOmg -> ANOM
            Kravgrunnlag.KravgrunnlagStatus.Avsluttet -> AVSL
            Kravgrunnlag.KravgrunnlagStatus.Ferdigbehandlet -> BEHA
            Kravgrunnlag.KravgrunnlagStatus.Endret -> ENDR
            Kravgrunnlag.KravgrunnlagStatus.Feil -> FEIL
            Kravgrunnlag.KravgrunnlagStatus.Manuell -> MANU
            Kravgrunnlag.KravgrunnlagStatus.Nytt -> NY
            Kravgrunnlag.KravgrunnlagStatus.Sperret -> SPER
        }
    }
}
