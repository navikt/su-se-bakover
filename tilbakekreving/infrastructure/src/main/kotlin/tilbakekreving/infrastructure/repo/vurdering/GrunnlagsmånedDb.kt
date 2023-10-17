package tilbakekreving.infrastructure.repo.vurdering

import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.tilMåned
import tilbakekreving.domain.kravgrunnlag.Kravgrunnlag
import tilbakekreving.infrastructure.repo.vurdering.FeilutbetalingDb.Companion.toDbJson
import tilbakekreving.infrastructure.repo.vurdering.YtelseDb.Companion.toDbJson
import java.math.BigDecimal
import java.time.YearMonth

internal data class GrunnlagsmånedDb(
    val måned: String,
    val betaltSkattForYtelsesgruppen: String,
    val ytelse: YtelseDb,
    val feilutbetaling: FeilutbetalingDb,
) {

    fun toDomain(): Kravgrunnlag.Grunnlagsmåned = Kravgrunnlag.Grunnlagsmåned(
        måned = Måned.fra(YearMonth.parse(this.måned)),
        betaltSkattForYtelsesgruppen = BigDecimal(this.betaltSkattForYtelsesgruppen),
        ytelse = this.ytelse.toDomain(),
        feilutbetaling = this.feilutbetaling.toDomain(),
    )

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
    val beløpTidligereUtbetaling: Int,
    val beløpNyUtbetaling: Int,
    val beløpSkalTilbakekreves: Int,
    val beløpSkalIkkeTilbakekreves: Int,
    val skatteProsent: String,
) {
    fun toDomain(): Kravgrunnlag.Grunnlagsmåned.Ytelse = Kravgrunnlag.Grunnlagsmåned.Ytelse(
        beløpTidligereUtbetaling = this.beløpTidligereUtbetaling,
        beløpNyUtbetaling = this.beløpNyUtbetaling,
        beløpSkalTilbakekreves = this.beløpSkalTilbakekreves,
        beløpSkalIkkeTilbakekreves = this.beløpSkalIkkeTilbakekreves,
        skatteProsent = BigDecimal(this.skatteProsent),
    )

    companion object {
        internal fun Kravgrunnlag.Grunnlagsmåned.Ytelse.toDbJson(): YtelseDb = YtelseDb(
            beløpTidligereUtbetaling = this.beløpTidligereUtbetaling,
            beløpNyUtbetaling = this.beløpNyUtbetaling,
            beløpSkalTilbakekreves = this.beløpSkalTilbakekreves,
            beløpSkalIkkeTilbakekreves = this.beløpSkalIkkeTilbakekreves,
            skatteProsent = this.skatteProsent.toString(),
        )
    }
}

internal data class FeilutbetalingDb(
    val beløpTidligereUtbetaling: Int,
    val beløpNyUtbetaling: Int,
    val beløpSkalTilbakekreves: Int,
    val beløpSkalIkkeTilbakekreves: Int,
) {
    fun toDomain(): Kravgrunnlag.Grunnlagsmåned.Feilutbetaling = Kravgrunnlag.Grunnlagsmåned.Feilutbetaling(
        beløpTidligereUtbetaling = this.beløpTidligereUtbetaling,
        beløpNyUtbetaling = this.beløpNyUtbetaling,
        beløpSkalTilbakekreves = this.beløpSkalTilbakekreves,
        beløpSkalIkkeTilbakekreves = this.beløpSkalIkkeTilbakekreves,
    )

    companion object {
        internal fun Kravgrunnlag.Grunnlagsmåned.Feilutbetaling.toDbJson(): FeilutbetalingDb = FeilutbetalingDb(
            beløpTidligereUtbetaling = this.beløpTidligereUtbetaling,
            beløpNyUtbetaling = this.beløpNyUtbetaling,
            beløpSkalTilbakekreves = this.beløpSkalTilbakekreves,
            beløpSkalIkkeTilbakekreves = this.beløpSkalIkkeTilbakekreves,
        )
    }
}
