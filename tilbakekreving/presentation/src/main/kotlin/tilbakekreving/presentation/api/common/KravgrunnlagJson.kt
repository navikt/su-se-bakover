package tilbakekreving.presentation.api.common

import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.serialize
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.presentation.api.common.GrunnlagsbeløpJson.Companion.toJson
import tilbakekreving.presentation.api.common.GrunnlagsperiodeJson.Companion.toJson
import tilbakekreving.presentation.api.common.KlasseKodeJson.Companion.toJson
import tilbakekreving.presentation.api.common.KlasseTypeJson.Companion.toJson
import tilbakekreving.presentation.api.common.KravgrunnlagStatusJson.Companion.toJson
import økonomi.domain.KlasseKode
import økonomi.domain.KlasseType

data class KravgrunnlagJson(
    val eksternKravgrunnlagsId: String,
    val eksternVedtakId: String,
    val kontrollfelt: String,
    val status: KravgrunnlagStatusJson,
    val grunnlagsperiode: List<GrunnlagsperiodeJson>,
) {

    companion object {
        fun Kravgrunnlag.toJson(): KravgrunnlagJson = KravgrunnlagJson(
            eksternKravgrunnlagsId = this.eksternKravgrunnlagId,
            eksternVedtakId = this.eksternVedtakId,
            kontrollfelt = this.eksternKontrollfelt,
            status = this.status.toJson(),
            grunnlagsperiode = this.grunnlagsperioder.toJson(),
        )

        fun Kravgrunnlag.toStringifiedJson(): String = serialize(this.toJson())
    }
}

data class GrunnlagsperiodeJson(
    // TODO bytt till et uuuu-MM format (f.eks. toString() av YearMonth)
    val periode: PeriodeJson,
    val beløpSkattMnd: String,
    val grunnlagsbeløp: List<GrunnlagsbeløpJson>,
) {
    companion object {
        fun List<Kravgrunnlag.Grunnlagsperiode>.toJson(): List<GrunnlagsperiodeJson> = this.map {
            GrunnlagsperiodeJson(
                periode = it.periode.toJson(),
                beløpSkattMnd = it.beløpSkattMnd.toString(),
                grunnlagsbeløp = it.grunnlagsbeløp.toJson(),
            )
        }
    }
}

data class GrunnlagsbeløpJson(
    val kode: KlasseKodeJson,
    val type: KlasseTypeJson,
    val beløpTidligereUtbetaling: String,
    val beløpNyUtbetaling: String,
    val beløpSkalTilbakekreves: String,
    val beløpSkalIkkeTilbakekreves: String,
    val skatteProsent: String,
) {
    companion object {
        fun List<Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp>.toJson(): List<GrunnlagsbeløpJson> = this.map {
            GrunnlagsbeløpJson(
                kode = it.kode.toJson(),
                type = it.type.toJson(),
                beløpTidligereUtbetaling = it.beløpTidligereUtbetaling.toString(),
                beløpNyUtbetaling = it.beløpNyUtbetaling.toString(),
                beløpSkalTilbakekreves = it.beløpSkalTilbakekreves.toString(),
                beløpSkalIkkeTilbakekreves = it.beløpSkalIkkeTilbakekreves.toString(),
                skatteProsent = it.skatteProsent.toString(),
            )
        }
    }
}

enum class KlasseKodeJson {
    SUUFORE,
    KL_KODE_FEIL_INNT,
    TBMOTOBS,
    FSKTSKAT,
    UFOREUT,
    SUALDER,
    KL_KODE_FEIL,
    ;

    companion object {
        fun KlasseKode.toJson(): KlasseKodeJson = when (this) {
            KlasseKode.SUUFORE -> SUUFORE
            KlasseKode.KL_KODE_FEIL_INNT -> KL_KODE_FEIL_INNT
            KlasseKode.TBMOTOBS -> TBMOTOBS
            KlasseKode.FSKTSKAT -> FSKTSKAT
            KlasseKode.UFOREUT -> UFOREUT
            KlasseKode.SUALDER -> SUALDER
            KlasseKode.KL_KODE_FEIL -> KL_KODE_FEIL
        }
    }
}

enum class KlasseTypeJson {
    YTEL,
    SKAT,
    FEIL,
    MOTP,
    ;

    companion object {
        fun KlasseType.toJson(): KlasseTypeJson = when (this) {
            KlasseType.YTEL -> YTEL
            KlasseType.SKAT -> SKAT
            KlasseType.FEIL -> FEIL
            KlasseType.MOTP -> MOTP
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
