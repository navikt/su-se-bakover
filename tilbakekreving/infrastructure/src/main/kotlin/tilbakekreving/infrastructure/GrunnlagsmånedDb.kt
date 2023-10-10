package tilbakekreving.infrastructure

import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.tilMåned
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.infrastructure.FeilutbetalingDb.Companion.toDbJson
import tilbakekreving.infrastructure.YtelseDb.Companion.toDbJson
import økonomi.domain.KlasseKode
import java.math.BigDecimal
import java.time.YearMonth

internal data class GrunnlagsmånedDb(
    val måned: String,
    val betaltSkattForYtelsesgruppen: String,
    val ytelse: YtelseDb,
    val feilutbetaling: FeilutbetalingDb,
) {

    fun toDomain(): Kravgrunnlag.Grunnlagsmåned {
        return Kravgrunnlag.Grunnlagsmåned(
            måned = Måned.fra(YearMonth.parse(this.måned)),
            betaltSkattForYtelsesgruppen = BigDecimal(this.betaltSkattForYtelsesgruppen),
            ytelse = this.ytelse.toDomain(),
            feilutbetaling = this.feilutbetaling.toDomain(),
        )
    }

    companion object {
        fun Kravgrunnlag.Grunnlagsmåned.toDbJson(): GrunnlagsmånedDb {
            return GrunnlagsmånedDb(
                // uuuu-MM
                måned = this.måned.tilMåned().toString(),
                betaltSkattForYtelsesgruppen = this.betaltSkattForYtelsesgruppen.toString(),
                ytelse = this.ytelse.toDbJson(),
                feilutbetaling = this.feilutbetaling.toDbJson(),
            )
        }
    }
}

internal data class YtelseDb(
    val type: String,
    val kode: String,
    val beløpTidligereUtbetaling: String,
    val beløpNyUtbetaling: String,
    val beløpSkalTilbakekreves: String,
    val beløpSkalIkkeTilbakekreves: String,
    val skatteProsent: String,
) {
    fun toDomain(): Kravgrunnlag.Grunnlagsmåned.Ytelse {
        return Kravgrunnlag.Grunnlagsmåned.Ytelse(
            beløpTidligereUtbetaling = this.beløpTidligereUtbetaling.toInt(),
            beløpNyUtbetaling = this.beløpNyUtbetaling.toInt(),
            beløpSkalTilbakekreves = this.beløpSkalTilbakekreves.toInt(),
            beløpSkalIkkeTilbakekreves = this.beløpSkalIkkeTilbakekreves.toInt(),
            skatteProsent = BigDecimal(this.skatteProsent),
            klassekode = this.kode.toKlasseKode(),
        )
    }

    companion object {
        internal fun Kravgrunnlag.Grunnlagsmåned.Ytelse.toDbJson(): YtelseDb = YtelseDb(
            kode = this.klassekode.toDb(),
            type = "YTEL",
            beløpTidligereUtbetaling = this.beløpTidligereUtbetaling.toString(),
            beløpNyUtbetaling = this.beløpNyUtbetaling.toString(),
            beløpSkalTilbakekreves = this.beløpSkalTilbakekreves.toString(),
            beløpSkalIkkeTilbakekreves = this.beløpSkalIkkeTilbakekreves.toString(),
            skatteProsent = this.skatteProsent.toString(),
        )
    }
}

internal data class FeilutbetalingDb(
    val kode: String,
    val beløpTidligereUtbetaling: String,
    val beløpNyUtbetaling: String,
    val beløpSkalTilbakekreves: String,
    val beløpSkalIkkeTilbakekreves: String,
) {
    fun toDomain(): Kravgrunnlag.Grunnlagsmåned.Feilutbetaling {
        return Kravgrunnlag.Grunnlagsmåned.Feilutbetaling(
            beløpTidligereUtbetaling = this.beløpTidligereUtbetaling.toInt(),
            beløpNyUtbetaling = this.beløpNyUtbetaling.toInt(),
            beløpSkalTilbakekreves = this.beløpSkalTilbakekreves.toInt(),
            beløpSkalIkkeTilbakekreves = this.beløpSkalIkkeTilbakekreves.toInt(),
            klassekode = this.kode.toKlasseKode(),
        )
    }

    companion object {
        internal fun Kravgrunnlag.Grunnlagsmåned.Feilutbetaling.toDbJson(): FeilutbetalingDb = FeilutbetalingDb(
            kode = this.klassekode.toDb(),
            beløpTidligereUtbetaling = this.beløpTidligereUtbetaling.toString(),
            beløpNyUtbetaling = this.beløpNyUtbetaling.toString(),
            beløpSkalTilbakekreves = this.beløpSkalTilbakekreves.toString(),
            beløpSkalIkkeTilbakekreves = this.beløpSkalIkkeTilbakekreves.toString(),
        )
    }
}

private fun String.toKlasseKode(): KlasseKode = when (this) {
    "SUUFORE" -> økonomi.domain.KlasseKode.SUUFORE
    "KL_KODE_FEIL_INNT" -> økonomi.domain.KlasseKode.KL_KODE_FEIL_INNT
    "TBMOTOBS" -> økonomi.domain.KlasseKode.TBMOTOBS
    "FSKTSKAT" -> økonomi.domain.KlasseKode.FSKTSKAT
    "UFOREUT" -> økonomi.domain.KlasseKode.UFOREUT
    "SUALDER" -> økonomi.domain.KlasseKode.SUALDER
    "KL_KODE_FEIL" -> økonomi.domain.KlasseKode.KL_KODE_FEIL
    else -> throw IllegalStateException("Ukjent persistert klassekode på KravgrunnlagPåSakHendelse: $this")
}

private fun KlasseKode.toDb(): String = when (this) {
    KlasseKode.SUUFORE -> "SUUFORE"
    KlasseKode.KL_KODE_FEIL_INNT -> "KL_KODE_FEIL_INNT"
    KlasseKode.TBMOTOBS -> "TBMOTOBS"
    KlasseKode.FSKTSKAT -> "FSKTSKAT"
    KlasseKode.UFOREUT -> "UFOREUT"
    KlasseKode.SUALDER -> "SUALDER"
    KlasseKode.KL_KODE_FEIL -> "KL_KODE_FEIL"
}
