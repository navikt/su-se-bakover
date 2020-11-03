package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.web.routes.behandling.SimuleringJson.SimulertPeriodeJson.Companion.toJson
import java.time.LocalDate

data class SimuleringJson(
    val totalBruttoYtelse: Double,
    val perioder: List<SimulertPeriodeJson>
) {
    companion object {
        fun Simulering.toJson(): SimuleringJson = SimuleringJson(
            totalBruttoYtelse = bruttoYtelse(),
            perioder = periodeList.toJson()
        )
    }

    data class SimulertPeriodeJson(
        val fraOgMed: LocalDate,
        val tilOgMed: LocalDate,
        val bruttoYtelse: Double
    ) {
        companion object {
            fun List<SimulertPeriode>.toJson() = this.map {
                SimulertPeriodeJson(
                    fraOgMed = it.fraOgMed,
                    tilOgMed = it.tilOgMed,
                    bruttoYtelse = it.bruttoYtelse()
                )
            }
        }
    }
}

data class UtbetalingslinjeJson(
    val id: String,
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val beløp: Double,
    val type: String
)
