package no.nav.su.se.bakover.tilbakekreving.presentation

import no.nav.su.se.bakover.common.infrastructure.PeriodeJson
import no.nav.su.se.bakover.common.infrastructure.PeriodeJson.Companion.toJson
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.tilbakekreving.domain.KlasseKode
import no.nav.su.se.bakover.tilbakekreving.domain.KlasseType
import no.nav.su.se.bakover.tilbakekreving.domain.Kravgrunnlag
import no.nav.su.se.bakover.tilbakekreving.presentation.GrunnlagsbeløpJson.Companion.toJson
import no.nav.su.se.bakover.tilbakekreving.presentation.GrunnlagsperiodeJson.Companion.toJson
import no.nav.su.se.bakover.tilbakekreving.presentation.KlasseKodeJson.Companion.toJson
import no.nav.su.se.bakover.tilbakekreving.presentation.KlasseTypeJson.Companion.toJson
import no.nav.su.se.bakover.tilbakekreving.presentation.KravgrunnlagStatusJson.Companion.toJson

data class KravgrunnlagJson(
    val eksternKravgrunnlagsId: String,
    val eksternVedtakId: String,
    val kontrollfelt: String,
    val status: KravgrunnlagStatusJson,
    val grunnlagsperiode: List<GrunnlagsperiodeJson>,
) {

    companion object {
        fun Kravgrunnlag.toJson(): KravgrunnlagJson = KravgrunnlagJson(
            eksternKravgrunnlagsId = this.kravgrunnlagId,
            eksternVedtakId = this.vedtakId,
            kontrollfelt = this.kontrollfelt,
            status = this.status.toJson(),
            grunnlagsperiode = this.grunnlagsperioder.toJson(),
        )

        fun Kravgrunnlag.toStringifiedJson(): String = serialize(this.toJson())
    }
}

data class GrunnlagsperiodeJson(
    // TODO bytt til månedJson
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
            Kravgrunnlag.KravgrunnlagStatus.ANNU -> ANNU
            Kravgrunnlag.KravgrunnlagStatus.ANOM -> ANOM
            Kravgrunnlag.KravgrunnlagStatus.AVSL -> AVSL
            Kravgrunnlag.KravgrunnlagStatus.BEHA -> BEHA
            Kravgrunnlag.KravgrunnlagStatus.ENDR -> ENDR
            Kravgrunnlag.KravgrunnlagStatus.FEIL -> FEIL
            Kravgrunnlag.KravgrunnlagStatus.MANU -> MANU
            Kravgrunnlag.KravgrunnlagStatus.NY -> NY
            Kravgrunnlag.KravgrunnlagStatus.SPER -> SPER
        }
    }
}
