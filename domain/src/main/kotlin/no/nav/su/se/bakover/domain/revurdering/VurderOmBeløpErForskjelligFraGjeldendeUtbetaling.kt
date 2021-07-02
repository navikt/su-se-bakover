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
            objekter = eksisterendeUtbetalinger,
        )
        resultat = nyBeregning.getMånedsberegninger()
            .any { it.getSumYtelse() != utbetalingstidslinje.gjeldendeForDato(it.periode.fraOgMed)?.beløp }
    }
}
