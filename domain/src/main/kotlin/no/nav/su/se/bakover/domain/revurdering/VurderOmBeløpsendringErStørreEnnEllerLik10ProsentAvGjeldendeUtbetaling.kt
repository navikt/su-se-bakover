package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import kotlin.math.abs

data class VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
    private val eksisterendeUtbetalinger: List<Utbetalingslinje>,
    private val nyBeregning: Beregning,
) {
    val resultat: Boolean

    init {
        fun diffEr10ProsentEllerMer(førsteMånedsbeløp: Int, gjeldendeUtbetalingsbeløp: Int) =
            abs(førsteMånedsbeløp - gjeldendeUtbetalingsbeløp) >= (0.1 * gjeldendeUtbetalingsbeløp)

        val utbetalingstidslinje = Tidslinje(
            periode = nyBeregning.periode,
            objekter = eksisterendeUtbetalinger,
        )

        val førsteMånedsberegning = nyBeregning.getMånedsberegninger()
            .minByOrNull { it.periode.fraOgMed }!!
        val gjeldendeUtbetaling = utbetalingstidslinje.gjeldendeForDato(førsteMånedsberegning.periode.fraOgMed)

        resultat = when (gjeldendeUtbetaling) {
            null -> true
            is Utbetalingslinje.Ny -> diffEr10ProsentEllerMer(
                førsteMånedsberegning.finnBeløpFor10ProsentSjekk(),
                gjeldendeUtbetaling.beløp,
            )
            is Utbetalingslinje.Endring -> {
                when (gjeldendeUtbetaling) {
                    is Utbetalingslinje.Endring.Opphør,
                    is Utbetalingslinje.Endring.Stans,
                    -> {
                        val statusFraOgMed = gjeldendeUtbetaling.virkningstidspunkt
                        val opphørEllerStansGjelderForHeleBeregningsperioden = nyBeregning.getMånedsberegninger()
                            .map { it.periode }
                            .map { utbetalingstidslinje.gjeldendeForDato(it.fraOgMed) }
                            .all { it == gjeldendeUtbetaling }
                        when {
                            /**
                             * Overser 10%-sjekk dersom opphøret gjelder for hele beregningsperioden og første måned
                             * i beregningen faller på opphørsdato eller senere. I praksis betyr dette at beløpet som
                             * utbetales er lik 0.
                             */
                            opphørEllerStansGjelderForHeleBeregningsperioden && (
                                statusFraOgMed.isEqual(førsteMånedsberegning.periode.fraOgMed) || statusFraOgMed.isBefore(
                                    førsteMånedsberegning.periode.fraOgMed,
                                )
                                ) -> {
                                diffEr10ProsentEllerMer(førsteMånedsberegning.finnBeløpFor10ProsentSjekk(), 0)
                            }
                            else -> diffEr10ProsentEllerMer(
                                førsteMånedsberegning.finnBeløpFor10ProsentSjekk(),
                                gjeldendeUtbetaling.beløp,
                            )
                        }
                    }
                    is Utbetalingslinje.Endring.Reaktivering -> diffEr10ProsentEllerMer(førsteMånedsberegning.finnBeløpFor10ProsentSjekk(), gjeldendeUtbetaling.beløp)
                }
            }
        }
    }

    /**
     * Dersom beløpet er så lavt at det havner under minstegrensen må vi sjekke fradragsbeløpet opp mot 10%
     */
    private fun Månedsberegning.finnBeløpFor10ProsentSjekk(): Int {
        return if (erSumYtelseUnderMinstebeløp()) {
            getFradrag()
                .filter { it.fradragstype == Fradragstype.UnderMinstenivå }
                .sumOf { it.månedsbeløp }
                .toInt()
        } else {
            getSumYtelse()
        }
    }
}
