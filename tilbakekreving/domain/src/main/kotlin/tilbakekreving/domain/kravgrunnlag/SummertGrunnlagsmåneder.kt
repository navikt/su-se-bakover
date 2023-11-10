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
        fun Kravgrunnlag.Grunnlagsmåned.Ytelse.netto(betaltSkattForYtelsesgruppen: BigDecimal): Beløp {
            val nettoBeløp = BigDecimal(beløpSkalTilbakekreves)
                .multiply(skatteProsent)
                .divide(BigDecimal("100"))
                .setScale(0, RoundingMode.DOWN)
                .min(betaltSkattForYtelsesgruppen)

            return Beløp(nettoBeløp.intValueExact())
        }
    }
}

fun List<Kravgrunnlag.Grunnlagsmåned>.total(): SummertGrunnlagsmåneder {
    return this.map {
        SummertGrunnlagsmåneder(
            betaltSkattForYtelsesgruppen = it.betaltSkattForYtelsesgruppen,
            beløpTidligereUtbetaling = it.ytelse.beløpTidligereUtbetaling,
            beløpNyUtbetaling = it.ytelse.beløpNyUtbetaling,
            beløpSkalTilbakekreves = it.ytelse.beløpSkalTilbakekreves,
            beløpSkalIkkeTilbakekreves = it.ytelse.beløpSkalIkkeTilbakekreves,
            nettoBeløp = it.ytelse.netto(it.betaltSkattForYtelsesgruppen),
        )
    }.reduce { acc, summert ->
        SummertGrunnlagsmåneder(
            betaltSkattForYtelsesgruppen = acc.betaltSkattForYtelsesgruppen + summert.betaltSkattForYtelsesgruppen,
            beløpTidligereUtbetaling = acc.beløpTidligereUtbetaling + summert.beløpTidligereUtbetaling,
            beløpNyUtbetaling = acc.beløpNyUtbetaling + summert.beløpNyUtbetaling,
            beløpSkalTilbakekreves = acc.beløpSkalTilbakekreves + summert.beløpSkalTilbakekreves,
            beløpSkalIkkeTilbakekreves = acc.beløpSkalIkkeTilbakekreves + summert.beløpSkalIkkeTilbakekreves,
            nettoBeløp = acc.nettoBeløp + summert.nettoBeløp,
        )
    }
}
