package no.nav.su.se.bakover.web.routes.søknadsbehandling

import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.TolketPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.TolketSimulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.TolketUtbetaling
import java.time.LocalDate

internal data class UtbetalingJson(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val beløp: Int,
    val type: String,
)

internal enum class SimulertUtbetalingstype {
    ETTERBETALING,
    FEILUTBETALING,
    ORDINÆR,
    UENDRET,
    INGEN_UTBETALING,
}

internal data class SimuleringJson(
    val perioder: List<SimulertPeriodeJson>,
    val totalBruttoYtelse: Int,
) {
    data class SimulertPeriodeJson(
        val fraOgMed: LocalDate,
        val tilOgMed: LocalDate,
        val type: SimulertUtbetalingstype,
        val bruttoYtelse: Int,
    )

    companion object {
        fun Simulering.toJson() = TolketSimulering(this).let {
            SimuleringJson(
                perioder = it.simulertePerioder.map { sp -> sp.toJson() },
                totalBruttoYtelse = it.simulertePerioder
                    .sumOf { sp -> sp.utbetaling.bruttobeløp() },
            )
        }
    }
}

internal fun TolketPeriode.toJson(): SimuleringJson.SimulertPeriodeJson {
    return when (utbetaling) {
        is TolketUtbetaling.Etterbetaling -> SimuleringJson.SimulertPeriodeJson(
            fraOgMed = periode.fraOgMed,
            tilOgMed = periode.tilOgMed,
            type = SimulertUtbetalingstype.ETTERBETALING,
            bruttoYtelse = utbetaling.bruttobeløp(),
        )
        is TolketUtbetaling.Feilutbetaling -> SimuleringJson.SimulertPeriodeJson(
            fraOgMed = periode.fraOgMed,
            tilOgMed = periode.tilOgMed,
            type = SimulertUtbetalingstype.FEILUTBETALING,
            bruttoYtelse = utbetaling.bruttobeløp()
                .times(-1),
        )
        is TolketUtbetaling.Ordinær -> SimuleringJson.SimulertPeriodeJson(
            fraOgMed = periode.fraOgMed,
            tilOgMed = periode.tilOgMed,
            type = SimulertUtbetalingstype.ORDINÆR,
            bruttoYtelse = utbetaling.bruttobeløp(),
        )
        is TolketUtbetaling.UendretUtbetaling -> SimuleringJson.SimulertPeriodeJson(
            fraOgMed = periode.fraOgMed,
            tilOgMed = periode.tilOgMed,
            type = SimulertUtbetalingstype.UENDRET,
            bruttoYtelse = utbetaling.bruttobeløp(),
        )

        is TolketUtbetaling.IngenUtbetaling -> SimuleringJson.SimulertPeriodeJson(
            fraOgMed = periode.fraOgMed,
            tilOgMed = periode.tilOgMed,
            type = SimulertUtbetalingstype.INGEN_UTBETALING,
            bruttoYtelse = utbetaling.bruttobeløp(),
        )
    }
}

object MerEnnEnUtbetalingIMinstEnAvPeriodene :
    IllegalStateException("En periode i simuleringen inneholdt mer enn én utbetaling. Dette støttes ikke p.t.")
