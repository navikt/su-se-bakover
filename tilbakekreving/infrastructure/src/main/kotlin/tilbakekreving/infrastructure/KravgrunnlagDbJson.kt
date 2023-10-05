package tilbakekreving.infrastructure

import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.tilMåned
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.infrastructure.KravgrunnlagDbJson.Grunnlagsmåned.Beløp.Companion.toDbJson
import tilbakekreving.infrastructure.KravgrunnlagDbJson.Grunnlagsmåned.Companion.toDbJson
import økonomi.domain.KlasseType
import java.math.BigDecimal
import java.time.YearMonth

/**
 * Databasejsontype for [tilbakekreving.domain.kravgrunnlag.Kravgrunnlag]
 */
internal data class KravgrunnlagDbJson(
    val eksternKravgrunnlagId: String,
    val eksternVedtakId: String,
    val eksternKontrollfelt: String,
    val status: String,
    val behandler: String,
    val utbetalingId: String,
    val saksnummer: String,
    val grunnlagsmåneder: List<Grunnlagsmåned>,
    val eksternTidspunkt: Tidspunkt,
) {
    data class Grunnlagsmåned(
        val måned: String,
        val betaltSkattForYtelsesgruppen: String,
        val grunnlagsbeløp: List<Beløp>,
    ) {

        data class Beløp(
            val kode: String,
            val type: String,
            val beløpTidligereUtbetaling: String,
            val beløpNyUtbetaling: String,
            val beløpSkalTilbakekreves: String,
            val beløpSkalIkkeTilbakekreves: String,
            val skatteProsent: String,
        ) {
            fun toDomain(): Kravgrunnlag.Grunnlagsmåned.Grunnlagsbeløp {
                return Kravgrunnlag.Grunnlagsmåned.Grunnlagsbeløp(
                    kode = when (this.kode) {
                        "SUUFORE" -> økonomi.domain.KlasseKode.SUUFORE
                        "KL_KODE_FEIL_INNT" -> økonomi.domain.KlasseKode.KL_KODE_FEIL_INNT
                        "TBMOTOBS" -> økonomi.domain.KlasseKode.TBMOTOBS
                        "FSKTSKAT" -> økonomi.domain.KlasseKode.FSKTSKAT
                        "UFOREUT" -> økonomi.domain.KlasseKode.UFOREUT
                        "SUALDER" -> økonomi.domain.KlasseKode.SUALDER
                        "KL_KODE_FEIL" -> økonomi.domain.KlasseKode.KL_KODE_FEIL
                        else -> throw IllegalStateException("Ukjent persistert klassekode på KravgrunnlagPåSakHendelse: ${this.kode}")
                    },
                    type = when (this.type) {
                        "YTEL" -> KlasseType.YTEL
                        "SKAT" -> KlasseType.SKAT
                        "FEIL" -> KlasseType.FEIL
                        "MOTP" -> KlasseType.MOTP
                        else -> throw IllegalStateException("Ukjent persistert klassetype på KravgrnunlagPåSakHendelse: ${this.type}")
                    },
                    beløpTidligereUtbetaling = BigDecimal(this.beløpTidligereUtbetaling),
                    beløpNyUtbetaling = BigDecimal(this.beløpNyUtbetaling),
                    beløpSkalTilbakekreves = BigDecimal(this.beløpSkalTilbakekreves),
                    beløpSkalIkkeTilbakekreves = BigDecimal(this.beløpSkalIkkeTilbakekreves),
                    skatteProsent = BigDecimal(this.skatteProsent),
                )
            }

            companion object {
                fun Kravgrunnlag.Grunnlagsmåned.Grunnlagsbeløp.toDbJson(): Beløp {
                    return Beløp(
                        kode = when (this.kode) {
                            økonomi.domain.KlasseKode.SUUFORE -> "SUUFORE"
                            økonomi.domain.KlasseKode.KL_KODE_FEIL_INNT -> "KL_KODE_FEIL_INNT"
                            økonomi.domain.KlasseKode.TBMOTOBS -> "TBMOTOBS"
                            økonomi.domain.KlasseKode.FSKTSKAT -> "FSKTSKAT"
                            økonomi.domain.KlasseKode.UFOREUT -> "UFOREUT"
                            økonomi.domain.KlasseKode.SUALDER -> "SUALDER"
                            økonomi.domain.KlasseKode.KL_KODE_FEIL -> "KL_KODE_FEIL"
                        },
                        type = when (this.type) {
                            KlasseType.YTEL -> "YTEL"
                            KlasseType.SKAT -> "SKAT"
                            KlasseType.FEIL -> "FEIL"
                            KlasseType.MOTP -> "MOTP"
                        },
                        beløpTidligereUtbetaling = this.beløpTidligereUtbetaling.toString(),
                        beløpNyUtbetaling = this.beløpNyUtbetaling.toString(),
                        beløpSkalTilbakekreves = this.beløpSkalTilbakekreves.toString(),
                        beløpSkalIkkeTilbakekreves = this.beløpSkalIkkeTilbakekreves.toString(),
                        skatteProsent = this.skatteProsent.toString(),
                    )
                }
            }
        }

        fun toDomain(): Kravgrunnlag.Grunnlagsmåned {
            return Kravgrunnlag.Grunnlagsmåned(
                måned = Måned.fra(YearMonth.parse(this.måned)),
                betaltSkattForYtelsesgruppen = BigDecimal(this.betaltSkattForYtelsesgruppen),
                grunnlagsbeløp = this.grunnlagsbeløp.map { it.toDomain() },
            )
        }

        companion object {
            fun Kravgrunnlag.Grunnlagsmåned.toDbJson(): Grunnlagsmåned {
                return Grunnlagsmåned(
                    måned = this.måned.tilMåned().toString(), // uuuu-MM
                    betaltSkattForYtelsesgruppen = this.betaltSkattForYtelsesgruppen.toString(),
                    grunnlagsbeløp = this.grunnlagsbeløp.map { it.toDbJson() },
                )
            }
        }
    }

    fun toDomain(): Kravgrunnlag {
        return Kravgrunnlag(
            eksternKravgrunnlagId = this.eksternKravgrunnlagId,
            eksternVedtakId = this.eksternVedtakId,
            eksternKontrollfelt = this.eksternKontrollfelt,
            status = when (this.status) {
                "Annulert" -> Kravgrunnlag.KravgrunnlagStatus.Annulert
                "AnnulertVedOmg" -> Kravgrunnlag.KravgrunnlagStatus.AnnulertVedOmg
                "Avsluttet" -> Kravgrunnlag.KravgrunnlagStatus.Avsluttet
                "Ferdigbehandlet" -> Kravgrunnlag.KravgrunnlagStatus.Ferdigbehandlet
                "Endret" -> Kravgrunnlag.KravgrunnlagStatus.Endret
                "Feil" -> Kravgrunnlag.KravgrunnlagStatus.Feil
                "Manuell" -> Kravgrunnlag.KravgrunnlagStatus.Manuell
                "Nytt" -> Kravgrunnlag.KravgrunnlagStatus.Nytt
                "Sperret" -> Kravgrunnlag.KravgrunnlagStatus.Sperret
                else -> throw IllegalStateException("Ukjent persistert kravgrunnlagsstatus på KravgrunnlagPåSakHendelse: ${this.status}")
            },
            behandler = this.behandler,
            utbetalingId = UUID30.fromString(this.utbetalingId),
            grunnlagsmåneder = this.grunnlagsmåneder.map { it.toDomain() },
            saksnummer = Saksnummer.parse(this.saksnummer),
            eksternTidspunkt = eksternTidspunkt,
        )
    }

    companion object {
        fun Kravgrunnlag.toDbJson(): KravgrunnlagDbJson {
            return KravgrunnlagDbJson(
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
                saksnummer = this.saksnummer.toString(),
                grunnlagsmåneder = this.grunnlagsmåneder.map { it.toDbJson() },
                eksternTidspunkt = eksternTidspunkt,
            )
        }
    }
}
