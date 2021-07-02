package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import kotlin.math.abs

data class VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
    private val eksisterendeUtbetalinger: List<Utbetalingslinje>,
    private val nyBeregning: Beregning,
) {
    val resultat: Boolean

    init {
        fun diffEr10ProsentEllerMer(førsteMånedsbeløp: Int, gjeldendeUtbetalingsbeløp: Int) =
            abs(førsteMånedsbeløp - gjeldendeUtbetalingsbeløp) >= (0.1 * gjeldendeUtbetalingsbeløp)

        val utbetalingstidslinje = TidslinjeForUtbetalinger(
            periode = nyBeregning.periode,
            objekter = eksisterendeUtbetalinger,
        )

        val førsteMånedsberegning = nyBeregning.getMånedsberegninger()
            .minByOrNull { it.periode.fraOgMed }!!
        val gjeldendeUtbetaling = utbetalingstidslinje.gjeldendeForDato(førsteMånedsberegning.periode.fraOgMed)

        resultat = diffEr10ProsentEllerMer(
            førsteMånedsbeløp = førsteMånedsberegning.finnBeløpFor10ProsentSjekk(),
            gjeldendeUtbetalingsbeløp = gjeldendeUtbetaling?.beløp ?: 0,
        )
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
