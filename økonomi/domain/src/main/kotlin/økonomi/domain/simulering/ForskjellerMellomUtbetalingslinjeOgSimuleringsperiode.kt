package økonomi.domain.simulering

import arrow.core.NonEmptyList
import no.nav.su.se.bakover.common.tid.periode.DatoIntervall
import no.nav.su.se.bakover.common.tid.periode.Periode

data class ForskjellerMellomUtbetalingOgSimulering(
    val feil: NonEmptyList<ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode>,
) : List<ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode> by feil

sealed interface ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode {
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
    ) : ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode

    /**
     *
     * @param periode Dette er kun utbetalingens periode, ikke simuleringens periode. Så denne vil være månedsbasert, men en periode kan ha flere perioder.
     */
    data class UliktBeløp(
        val periode: Periode,
        val simulertBeløp: Int,
        val utbetalingsbeløp: Int,
    ) : ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode

    /**
     * @param utbetalingsperiode Dette er kun utbetalingens periode, ikke simuleringens periode. Så denne vil være månedsbasert, men en periode kan ha flere perioder.
     * erNesteÅrISimulering: Hvis neste år og beløpene får mismatch er det mest sannsynlig økonomi som må aktivere simulering for neste år
     */
    data class UlikPeriode(
        val utbetalingsperiode: Periode,
        val simuleringsperiode: DatoIntervall?,
        val simulertBeløp: Int,
        val utbetalingsbeløp: Int,
        val erNesteÅrISimulering: Boolean,
    ) : ForskjellerMellomUtbetalingslinjeOgSimuleringsperiode
}
