package no.nav.su.se.bakover.domain.revurdering.beregning

import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Merknad
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger
import kotlin.math.abs
import kotlin.math.roundToInt

data class VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
    private val eksisterendeUtbetalinger: List<Utbetalingslinje>,
    private val nyBeregning: Beregning,
) {
    val resultat: Boolean

    init {
        val utbetalingstidslinje = TidslinjeForUtbetalinger(
            periode = nyBeregning.periode,
            utbetalingslinjer = eksisterendeUtbetalinger,
        )

        val førsteMånedsberegning = nyBeregning.getMånedsberegninger()
            .minByOrNull { it.periode.fraOgMed }!!

        val gjeldendeUtbetaling =
            utbetalingstidslinje.gjeldendeForDato(førsteMånedsberegning.periode.fraOgMed)?.beløp ?: 0

        val førsteMånedsbeløp = førsteMånedsberegning.finnBeløpFor10ProsentSjekk()

        resultat = when {
            førsteMånedsbeløp == 0 && gjeldendeUtbetaling == 0 -> {
                false
            }
            førsteMånedsbeløp == 0 && gjeldendeUtbetaling != 0 -> {
                true
            }
            førsteMånedsbeløp != 0 && gjeldendeUtbetaling == 0 -> {
                true
            }
            else -> {
                abs(førsteMånedsbeløp - gjeldendeUtbetaling) >= (0.1 * gjeldendeUtbetaling)
            }
        }
    }

    /**
     * Dersom beløpet er så lavt at det havner under minstegrensen må vi sjekke fradragsbeløpet opp mot 10%
     */
    private fun Månedsberegning.finnBeløpFor10ProsentSjekk(): Int {
        return if (getMerknader().contains(Merknad.Beregning.Avslag.BeløpMellomNullOgToProsentAvHøySats)) {
            getFradrag()
                .filter { it.fradragstype == Fradragstype.UnderMinstenivå }
                .sumOf { it.månedsbeløp }
                .roundToInt()
        } else {
            getSumYtelse()
        }
    }
}
