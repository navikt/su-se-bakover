package tilbakekreving.infrastructure

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import tilbakekreving.domain.kravgrunnlag.Grunnlagsmåned
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagPåSakHendelse
import økonomi.domain.KlasseType
import java.math.BigDecimal
import java.time.YearMonth
import java.util.UUID

fun KravgrunnlagPåSakHendelse.toJson(): String {
    return KravgrunnlagPåSakDbJson(
        eksternKravgrunnlagId = this.eksternKravgrunnlagId,
        eksternVedtakId = this.eksternVedtakId,
        eksternKontrollfelt = this.eksternKontrollfelt,
        status = when (this.status) {
            Kravgrunnlag.KravgrunnlagStatus.Annulert -> "Annullert"
            Kravgrunnlag.KravgrunnlagStatus.AnnulertVedOmg -> "AnnulertVedOmg"
            Kravgrunnlag.KravgrunnlagStatus.Avsluttet -> "Avsluttet"
            Kravgrunnlag.KravgrunnlagStatus.Ferdigbehandlet -> "Ferdigbehandlet"
            Kravgrunnlag.KravgrunnlagStatus.Endret -> "Endret"
            Kravgrunnlag.KravgrunnlagStatus.Feil -> "Feil"
            Kravgrunnlag.KravgrunnlagStatus.Manuell -> "Manuell"
            Kravgrunnlag.KravgrunnlagStatus.Nytt -> "Nytt"
            Kravgrunnlag.KravgrunnlagStatus.Sperret -> "Sperret"
        },
        behandler = this.behandler,
        utbetalingId = this.utbetalingId.value,
        grunnlagsmåneder = this.grunnlagsmåneder.map { grunnlagsmåned ->
            GrunnlagsmånedDbJson(
                måned = grunnlagsmåned.måned.toString(), // uuuu-MM
                betaltSkattForYtelsesgruppen = grunnlagsmåned.betaltSkattForYtelsesgruppen.toString(),
                grunnlagsbeløp = grunnlagsmåned.grunnlagsbeløp.map { grunnlagsbeløp ->
                    GrunnlagsbeløpDbJson(
                        kode = when (grunnlagsbeløp.kode) {
                            økonomi.domain.KlasseKode.SUUFORE -> "SUUFORE"
                            økonomi.domain.KlasseKode.KL_KODE_FEIL_INNT -> "KL_KODE_FEIL_INNT"
                            økonomi.domain.KlasseKode.TBMOTOBS -> "TBMOTOBS"
                            økonomi.domain.KlasseKode.FSKTSKAT -> "FSKTSKAT"
                            økonomi.domain.KlasseKode.UFOREUT -> "UFOREUT"
                            økonomi.domain.KlasseKode.SUALDER -> "SUALDER"
                            økonomi.domain.KlasseKode.KL_KODE_FEIL -> "KL_KODE_FEIL"
                        },
                        type = when (grunnlagsbeløp.type) {
                            KlasseType.YTEL -> "YTEL"
                            KlasseType.SKAT -> "SKAT"
                            KlasseType.FEIL -> "FEIL"
                            KlasseType.MOTP -> "MOTP"
                        },
                        beløpTidligereUtbetaling = grunnlagsbeløp.beløpTidligereUtbetaling.toString(),
                        beløpNyUtbetaling = grunnlagsbeløp.beløpNyUtbetaling.toString(),
                        beløpSkalTilbakekreves = grunnlagsbeløp.beløpSkalTilbakekreves.toString(),
                        beløpSkalIkkeTilbakekreves = grunnlagsbeløp.beløpSkalIkkeTilbakekreves.toString(),
                        skatteProsent = grunnlagsbeløp.skatteProsent.toString(),
                    )
                },

            )
        },
        revurderingId = revurderingId?.toString(),
    ).let {
        serialize(it)
    }
}

fun PersistertHendelse.toKravgrunnlagPåSakHendelse(): KravgrunnlagPåSakHendelse {
    return deserialize<KravgrunnlagPåSakDbJson>(this.data).let { json ->
        KravgrunnlagPåSakHendelse.fraPersistert(
            hendelseId = this.hendelseId,
            hendelsestidspunkt = this.hendelsestidspunkt,
            hendelseMetadata = this.hendelseMetadata,
            forrigeVersjon = this.versjon,
            entitetId = this.entitetId,
            sakId = this.sakId!!,
            tidligereHendelseId = this.tidligereHendelseId!!,
            eksternKravgrunnlagId = json.eksternKravgrunnlagId,
            eksternVedtakId = json.eksternVedtakId,
            eksternKontrollfelt = json.eksternKontrollfelt,
            status = when (json.status) {
                "Annulert" -> Kravgrunnlag.KravgrunnlagStatus.Annulert
                "AnnulertVedOmg" -> Kravgrunnlag.KravgrunnlagStatus.AnnulertVedOmg
                "Avsluttet" -> Kravgrunnlag.KravgrunnlagStatus.Avsluttet
                "Ferdigbehandlet" -> Kravgrunnlag.KravgrunnlagStatus.Ferdigbehandlet
                "Endret" -> Kravgrunnlag.KravgrunnlagStatus.Endret
                "Feil" -> Kravgrunnlag.KravgrunnlagStatus.Feil
                "Manuell" -> Kravgrunnlag.KravgrunnlagStatus.Manuell
                "Nytt" -> Kravgrunnlag.KravgrunnlagStatus.Nytt
                "Sperret" -> Kravgrunnlag.KravgrunnlagStatus.Sperret
                else -> throw IllegalStateException("Ukjent persistert kravgrunnlagsstatus på KravgrunnlagPåSakHendelse: ${json.status}")
            },
            behandler = json.behandler,
            utbetalingId = UUID30.fromString(json.utbetalingId),
            grunnlagsmåneder = json.grunnlagsmåneder.map {
                Grunnlagsmåned(
                    måned = Måned.fra(YearMonth.parse(it.måned)),
                    betaltSkattForYtelsesgruppen = BigDecimal(it.betaltSkattForYtelsesgruppen),
                    grunnlagsbeløp = it.grunnlagsbeløp.map {
                        Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp(
                            kode = when (it.kode) {
                                "SUUFORE" -> økonomi.domain.KlasseKode.SUUFORE
                                "KL_KODE_FEIL_INNT" -> økonomi.domain.KlasseKode.KL_KODE_FEIL_INNT
                                "TBMOTOBS" -> økonomi.domain.KlasseKode.TBMOTOBS
                                "FSKTSKAT" -> økonomi.domain.KlasseKode.FSKTSKAT
                                "UFOREUT" -> økonomi.domain.KlasseKode.UFOREUT
                                "SUALDER" -> økonomi.domain.KlasseKode.SUALDER
                                "KL_KODE_FEIL" -> økonomi.domain.KlasseKode.KL_KODE_FEIL
                                else -> throw IllegalStateException("Ukjent persistert klassekode på KravgrunnlagPåSakHendelse: ${it.kode}")
                            },
                            type = when (it.type) {
                                "YTEL" -> KlasseType.YTEL
                                "SKAT" -> KlasseType.SKAT
                                "FEIL" -> KlasseType.FEIL
                                "MOTP" -> KlasseType.MOTP
                                else -> throw IllegalStateException("Ukjent persistert klassetype på KravgrnunlagPåSakHendelse: ${it.type}")
                            },
                            beløpTidligereUtbetaling = BigDecimal(it.beløpTidligereUtbetaling),
                            beløpNyUtbetaling = BigDecimal(it.beløpNyUtbetaling),
                            beløpSkalTilbakekreves = BigDecimal(it.beløpSkalTilbakekreves),
                            beløpSkalIkkeTilbakekreves = BigDecimal(it.beløpSkalIkkeTilbakekreves),
                            skatteProsent = BigDecimal(it.skatteProsent),
                        )
                    }.toNonEmptyList(),
                )
            }.toNonEmptyList(),
            revurderingId = json.revurderingId?.let { UUID.fromString(it) },
        )
    }
}

/**
 * @see [tilbakekreving.domain.kravgrunnlag.KravgrunnlagPåSakHendelse]
 */
private data class KravgrunnlagPåSakDbJson(
    val eksternKravgrunnlagId: String,
    val eksternVedtakId: String,
    val eksternKontrollfelt: String,
    val status: String,
    val behandler: String,
    val utbetalingId: String,
    val grunnlagsmåneder: List<GrunnlagsmånedDbJson>,
    val revurderingId: String?,
)

/**
 * @see [tilbakekreving.domain.kravgrunnlag.Grunnlagsmåned]
 */
private data class GrunnlagsmånedDbJson(
    val måned: String,
    val betaltSkattForYtelsesgruppen: String,
    val grunnlagsbeløp: List<GrunnlagsbeløpDbJson>,
)

/**
 * @see [tilbakekreving.domain.kravgrunnlag.Kravgrunnlag.Grunnlagsperiode.Grunnlagsbeløp]
 */
private data class GrunnlagsbeløpDbJson(
    val kode: String,
    val type: String,
    val beløpTidligereUtbetaling: String,
    val beløpNyUtbetaling: String,
    val beløpSkalTilbakekreves: String,
    val beløpSkalIkkeTilbakekreves: String,
    val skatteProsent: String,
)
