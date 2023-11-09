package tilbakekreving.domain.kravgrunnlag

import no.nav.su.se.bakover.common.Beløp
import tilbakekreving.domain.kravgrunnlag.SummertGrunnlagsmåneder.Companion.netto
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Summerer sammen feltene som lar seg gjøres i hver grunnlagsmåned for ytelse.
 *
 */
data class SummertGrunnlagsmåneder(
    val betaltSkattForYtelsesgruppen: BigDecimal,
    val beløpTidligereUtbetaling: Int,
    val beløpNyUtbetaling: Int,
    val beløpSkalTilbakekreves: Int,
    val beløpSkalIkkeTilbakekreves: Int,
    val nettoBeløp: Beløp,
) {
    companion object {
        fun Kravgrunnlag.Grunnlagsmåned.Ytelse.netto(): Beløp {
            val bruttobeløp = BigDecimal(beløpSkalTilbakekreves)
            val skatteBeløp = skatteProsent / BigDecimal(100) * bruttobeløp
            val nettoBeløp = bruttobeløp - skatteBeløp
            val min = bruttobeløp.min(nettoBeløp)
            return Beløp(min.setScale(0, RoundingMode.DOWN).intValueExact())
        }
    }
}

fun List<Kravgrunnlag.Grunnlagsmåned>.total(): SummertGrunnlagsmåneder {
    val betaltSkattForYtelsesgruppen =
        this.map { it.betaltSkattForYtelsesgruppen }.reduce { acc, bigDecimal -> acc + bigDecimal }
    val beløpTidligereUtbetaling = this.map { it.ytelse.beløpTidligereUtbetaling }.reduce { acc, i -> acc + i }
    val beløpNyUtbetaling = this.map { it.ytelse.beløpNyUtbetaling }.reduce { acc, i -> acc + i }
    val beløpSkalTilbakekreves = this.map { it.ytelse.beløpSkalTilbakekreves }.reduce { acc, i -> acc + i }
    val beløpSkalIkkeTilbakekreves = this.map { it.ytelse.beløpSkalIkkeTilbakekreves }.reduce { acc, i -> acc + i }
    val nettoBeløp = this.map { it.ytelse.netto() }.reduce { acc, i -> acc + i }

    return SummertGrunnlagsmåneder(
        betaltSkattForYtelsesgruppen,
        beløpTidligereUtbetaling,
        beløpNyUtbetaling,
        beløpSkalTilbakekreves,
        beløpSkalIkkeTilbakekreves,
        nettoBeløp,
    )
}
