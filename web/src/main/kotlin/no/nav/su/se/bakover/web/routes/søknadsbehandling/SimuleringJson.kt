package no.nav.su.se.bakover.web.routes.søknadsbehandling

import økonomi.domain.simulering.PeriodeOppsummering
import økonomi.domain.simulering.Simulering
import økonomi.domain.simulering.SimuleringsOppsummering
import java.time.LocalDate

internal data class UtbetalingJson(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val beløp: Int,
    val type: String,
)
internal data class SimuleringJson(
    val totalOppsummering: PeriodeOppsummeringJson,
    val periodeOppsummering: List<PeriodeOppsummeringJson> = emptyList(),
) {
    companion object {
        fun Simulering.toJson(): SimuleringJson {
            return oppsummering().toJson().let {
                SimuleringJson(
                    it.totalOppsummering,
                    it.periodeOppsummering,
                )
            }
        }
    }
}

internal fun SimuleringsOppsummering.toJson(): SimuleringsOppsummeringJson {
    return SimuleringsOppsummeringJson(
        totalOppsummering = totalOppsummering.toJson(),
        periodeOppsummering = periodeOppsummering.map { it.toJson() },
    )
}

internal data class SimuleringsOppsummeringJson(
    val totalOppsummering: PeriodeOppsummeringJson,
    val periodeOppsummering: List<PeriodeOppsummeringJson>,
)

internal fun PeriodeOppsummering.toJson(): PeriodeOppsummeringJson {
    return PeriodeOppsummeringJson(
        fraOgMed = periode.fraOgMed,
        tilOgMed = periode.tilOgMed,
        sumTilUtbetaling = sumTilUtbetaling,
        sumEtterbetaling = sumEtterbetaling,
        sumFramtidigUtbetaling = sumFramtidigUtbetaling,
        sumTotalUtbetaling = sumTotalUtbetaling,
        sumTidligereUtbetalt = sumTidligereUtbetalt,
        sumFeilutbetaling = sumFeilutbetaling,
        sumReduksjonFeilkonto = sumReduksjonFeilkonto,
    )
}

internal data class PeriodeOppsummeringJson(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val sumTilUtbetaling: Int,
    val sumEtterbetaling: Int,
    val sumFramtidigUtbetaling: Int,
    val sumTotalUtbetaling: Int,
    val sumTidligereUtbetalt: Int,
    val sumFeilutbetaling: Int,
    val sumReduksjonFeilkonto: Int,
)
