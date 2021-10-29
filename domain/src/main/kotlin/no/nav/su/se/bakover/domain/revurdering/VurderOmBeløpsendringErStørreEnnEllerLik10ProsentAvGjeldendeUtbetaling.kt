package no.nav.su.se.bakover.domain.revurdering

import no.nav.su.se.bakover.common.procentuellDifferens
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.tidslinje.TidslinjeForUtbetalinger

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
        val gjeldendeUtbetaling = utbetalingstidslinje.gjeldendeForDato(førsteMånedsberegning.periode.fraOgMed)

        val nyttMånedsbeløp = førsteMånedsberegning.finnBeløpFor10ProsentSjekk()
        val gjeldendeUtbetalingsbeløp = gjeldendeUtbetaling?.beløp ?: 0

        resultat = procentuellDifferens(gjeldendeUtbetalingsbeløp, nyttMånedsbeløp).fold(
            ifLeft = { økningFra0ErMer10Prosent(nyttMånedsbeløp) },
            ifRight = { økning ->
                when {
                    økning < 0 -> økning <= -0.1
                    else -> økning >= 0.1
                }
            }
        )
    }

    /**
     * Økning fra 0 er ikke matematiskt definiert, men i vårt case så vi aksepterer følgende:
     * 0 -> 0 >= 0.1 = false
     * 0 -> x >= 0.1 = true, där x är ett positivt heltal (ikke 0)
     * */
    private fun økningFra0ErMer10Prosent(nyVerdi: Int) = when (nyVerdi) {
        0 -> false
        else -> true
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
