package økonomi.domain.simulering

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.common.tid.periode.Periode

data class ForskjellerMellomUtbetalingOgSimulering(
    val feil: NonEmptyList<ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode>,
) : List<ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode> by feil {
    init {
        require(feil.sorted() == feil) {
            "Feil må være sortert etter prioritet"
        }
    }
}

sealed class ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode(val prioritet: Int) : Comparable<ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode> {
    /**
     * TODO jah: I neste PR gå over simulertType / tidslinjeType. Og se på mulighet for ikke å ha en egen case av "oppdrag ga tom respons", siden det ikke er vårt domene.
     *
     * Simuleringen i sin helhet ga tom respons (ingen utbetalinger har skjedd eller skal skje).
     * Vi forventet at alle utbetalingslinjene var 0, men det var de ikke.
     * Vil få en slik per utbetalingslinje.
     *
     * @param periode Dette er kun utbetalingens periode, ikke simuleringens periode. Så denne vil være månedsbasert, men en periode kan ha flere perioder.
     *
     */
    data class UtbetalingslinjeVarIkke0(
        val periode: Periode,
        val beløp: Int,
    ) : ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode(prioritet = 2)

    /**
     *
     * @param periode Dette er kun utbetalingens periode, ikke simuleringens periode. Så denne vil være månedsbasert, men en periode kan ha flere perioder.
     */
    data class UliktBeløp(
        val periode: Periode,
        val simulertBeløp: Int,
        val utbetalingsbeløp: Int,
    ) : ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode(prioritet = 2)

    /**
     * @param utbetalingsperiode Dette er kun utbetalingens periode, ikke simuleringens periode. Så denne vil være månedsbasert, men en periode kan ha flere perioder.
     */
    data class UlikPeriode(
        val utbetalingsperiode: Periode,
        val simuleringsperiode: DatoIntervall?,
        val simulertBeløp: Int,
        val utbetalingsbeløp: Int,
    ) : ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode(prioritet = 2)

    override fun compareTo(other: ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode): Int {
        return this.prioritet.compareTo(other.prioritet)
    }
}
