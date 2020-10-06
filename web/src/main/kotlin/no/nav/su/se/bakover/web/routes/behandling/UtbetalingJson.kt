package no.nav.su.se.bakover.web.routes.behandling

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulertPeriode
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.routes.behandling.UtbetalingJson.SimuleringJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.behandling.UtbetalingJson.SimuleringJson.SimulertPeriodeJson.Companion.toJson
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class UtbetalingJson(
    val id: String,
    val opprettet: String,
    val simulering: SimuleringJson?,
) {
    companion object {
        fun Utbetaling.toJson() =
            UtbetalingJson(
                id = id.toString(),
                opprettet = DateTimeFormatter.ISO_INSTANT.format(opprettet),
                simulering = simulering?.toJson(),
            )

        internal fun HttpStatusCode.jsonBody(utbetaling: Utbetaling) =
            Resultat.json(this, serialize(utbetaling.toJson()))
    }

    data class SimuleringJson(
        val totalBruttoYtelse: Int,
        val perioder: List<SimulertPeriodeJson>
    ) {
        companion object {
            fun Simulering.toJson() = SimuleringJson(
                totalBruttoYtelse = bruttoYtelse(),
                perioder = periodeList.toJson()
            )
        }

        data class SimulertPeriodeJson(
            val fom: LocalDate,
            val fraOgMed: LocalDate,
            val tom: LocalDate,
            val tilOgMed: LocalDate,
            val bruttoYtelse: Int
        ) {
            companion object {
                fun List<SimulertPeriode>.toJson() = this.map {
                    SimulertPeriodeJson(
                        fom = it.fraOgMed,
                        fraOgMed = it.fraOgMed,
                        tom = it.tilOgMed,
                        tilOgMed = it.tilOgMed,
                        bruttoYtelse = it.bruttoYtelse()
                    )
                }
            }
        }
    }
}
