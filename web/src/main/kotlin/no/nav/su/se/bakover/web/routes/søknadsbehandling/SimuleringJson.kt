package no.nav.su.se.bakover.web.routes.søknadsbehandling

import no.nav.su.se.bakover.domain.oppdrag.simulering.Kontooppstilling
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import java.time.LocalDate

internal data class UtbetalingJson(
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val beløp: Int,
    val type: String,
)
data class KontooppstillingJson(
    val simulertUtbetaling: Int,
    val debetYtelse: Int,
    val kreditYtelse: Int,
    val debetFeilkonto: Int,
    val kreditFeilkonto: Int,
    val debetMotpostFeilkonto: Int,
    val kreditMotpostFeilkonto: Int,
    val sumYtelse: Int,
    val sumFeilkonto: Int,
    val sumMotpostFeilkonto: Int,
)
internal data class SimuleringJson(
    val totalBruttoYtelse: Int,
    val perioder: List<NySimulertPeriodeJson> = emptyList(),
) {
    data class NySimulertPeriodeJson(
        val fraOgMed: LocalDate,
        val tilOgMed: LocalDate,
        val kontooppstilling: KontooppstillingJson,
    )

    companion object {
        fun Simulering.toJson(): SimuleringJson {
            return SimuleringJson(
                totalBruttoYtelse = hentTilUtbetaling().sum(),
                perioder = kontooppstilling().map { (måned, kontooppstilling) ->
                    NySimulertPeriodeJson(
                        fraOgMed = måned.fraOgMed,
                        tilOgMed = måned.tilOgMed,
                        kontooppstilling = kontooppstilling.toJson(),
                    )
                },
            )
        }
    }
}

internal fun Kontooppstilling.toJson(): KontooppstillingJson {
    return KontooppstillingJson(
        debetYtelse = debetYtelse.sum(),
        simulertUtbetaling = simulertUtbetaling.sum(),
        kreditYtelse = kreditYtelse.sum(),
        debetFeilkonto = debetFeilkonto.sum(),
        kreditFeilkonto = kreditFeilkonto.sum(),
        debetMotpostFeilkonto = debetMotpostFeilkonto.sum(),
        kreditMotpostFeilkonto = kreditMotpostFeilkonto.sum(),
        sumYtelse = sumUtbetaling.sum(),
        sumFeilkonto = sumFeilkonto.sum(),
        sumMotpostFeilkonto = sumMotpostFeilkonto.sum(),
    )
}
