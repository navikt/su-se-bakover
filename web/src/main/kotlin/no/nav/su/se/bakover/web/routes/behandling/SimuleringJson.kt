package no.nav.su.se.bakover.web.routes.behandling

import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.web.routes.behandling.SimuleringJson.SimulertPeriodeJson.Companion.toJson
import java.time.LocalDate

data class SimuleringJson(
    val totalBruttoYtelse: Int,
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
        val bruttoYtelse: Int
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

data class UtbetalingJson(
    val id: String,
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val beløp: Int,
    val type: String
)
