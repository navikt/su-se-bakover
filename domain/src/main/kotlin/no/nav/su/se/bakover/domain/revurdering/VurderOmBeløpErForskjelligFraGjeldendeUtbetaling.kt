package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger

data class VurderOmBeløpErForskjelligFraGjeldendeUtbetaling(
    private val eksisterendeUtbetalinger: List<Utbetalingslinje>,
    private val nyBeregning: Beregning,
) {
    val resultat: Boolean

    init {
        val utbetalingstidslinje = TidslinjeForUtbetalinger(
            periode = nyBeregning.periode,
            utbetalingslinjer = eksisterendeUtbetalinger,
        )
        resultat = nyBeregning.getMånedsberegninger()
            .any {
                val gjeldendeUtbetaling = utbetalingstidslinje.gjeldendeForDato(it.periode.fraOgMed) ?: throw IllegalStateException("Kan ikke vurdere revurdering som mangler utbetaling")
                it.getSumYtelse() != gjeldendeUtbetaling.beløp
            }
    }
}
