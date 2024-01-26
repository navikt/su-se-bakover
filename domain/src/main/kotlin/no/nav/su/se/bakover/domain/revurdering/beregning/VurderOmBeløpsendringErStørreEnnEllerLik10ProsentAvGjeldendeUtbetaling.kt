package no.nav.su.se.bakover.domain.revurdering.beregning

import beregning.domain.Beregning
import beregning.domain.Merknad
import beregning.domain.Månedsberegning
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.TidslinjeForUtbetalinger
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.Utbetalinger
import vilkår.inntekt.domain.grunnlag.Fradragstype
import kotlin.math.abs
import kotlin.math.roundToInt

data class VurderOmBeløpsendringErStørreEnnEllerLik10ProsentAvGjeldendeUtbetaling(
    private val eksisterendeUtbetalinger: Utbetalinger,
    private val nyBeregning: Beregning,
) {
    val resultat: Boolean

    init {
        require(eksisterendeUtbetalinger.isNotEmpty())
        val utbetalingstidslinje =
            TidslinjeForUtbetalinger.fra(utbetalinger = eksisterendeUtbetalinger)!!.krympTilPeriode(nyBeregning.periode)

        val førsteMånedsberegning = nyBeregning.getMånedsberegninger()
            .minByOrNull { it.periode.fraOgMed }!!

        val gjeldendeUtbetaling =
            utbetalingstidslinje?.gjeldendeForDato(førsteMånedsberegning.periode.fraOgMed)?.beløp ?: 0

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
