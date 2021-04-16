package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.TolketDetalj
import no.nav.su.se.bakover.domain.oppdrag.simulering.TolketPeriode
import no.nav.su.se.bakover.domain.oppdrag.simulering.TolketSimulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.TolketUtbetaling
import java.time.LocalDate

data class UtbetalingJson(
    val id: String,
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val beløp: Int,
    val type: String
)

enum class SimulertUtbetalingstype {
    ETTERBETALING,
    FEILUTBETALING,
    ORDINÆR
}

data class SimuleringJson(
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
                    .sumBy { sp ->
                        sp.utbetalinger
                            .sumBy { u -> u.bruttobeløp() }
                    },
            )
        }
    }
}

fun TolketPeriode.toJson(): SimuleringJson.SimulertPeriodeJson {
    val utbetaling = utbetalinger.singleOrNull() ?: throw MerEnnEnUtbetalingIMinstEnAvPeriodene
    return when (utbetaling) {
        is TolketUtbetaling.Etterbetaling -> SimuleringJson.SimulertPeriodeJson(
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            type = SimulertUtbetalingstype.ETTERBETALING,
            bruttoYtelse = utbetaling.tolketDetalj
                .filterIsInstance<TolketDetalj.Etterbetaling>()
                .sumBy { it.beløp },
        )
        is TolketUtbetaling.Feilutbetaling -> SimuleringJson.SimulertPeriodeJson(
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            type = SimulertUtbetalingstype.FEILUTBETALING,
            bruttoYtelse = utbetaling.tolketDetalj
                .filterIsInstance<TolketDetalj.Feilutbetaling>()
                .sumBy { it.beløp }
                .times(-1),
        )
        is TolketUtbetaling.Ordinær -> SimuleringJson.SimulertPeriodeJson(
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            type = SimulertUtbetalingstype.ORDINÆR,
            bruttoYtelse = utbetaling.tolketDetalj
                .filterIsInstance<TolketDetalj.Ordinær>()
                .sumBy { it.beløp },
        )
    }
}

object MerEnnEnUtbetalingIMinstEnAvPeriodene :
    IllegalStateException("En periode i simuleringen inneholdt mer enn èn utbetaling. Dette støttes ikke p.t.")
