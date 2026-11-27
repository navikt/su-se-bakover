package no.nav.su.se.bakover.database.historisk

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.HistoriskInfotrygdSimulering
import no.nav.su.se.bakover.domain.historisk.revurdering.utbetaling.HistoriskSimuleringsrespons
import java.time.LocalDate

internal data class HistoriskSimuleringDbJson(
    val grunnlag: HistoriskInfotrygdBeregningDbJson,
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate,
    val simulert: Tidspunkt,
    val harEksisterendeSuOppdrag: Boolean,
    val respons: HistoriskSimuleringsrespons,
) {
    fun toDomain() = HistoriskInfotrygdSimulering(
        grunnlag = grunnlag.toDomain(),
        simuleringsperiode = Periode.create(fraOgMed, tilOgMed),
        simulert = simulert,
        harEksisterendeSuOppdrag = harEksisterendeSuOppdrag,
        respons = respons,
    )
}

internal fun HistoriskInfotrygdSimulering?.serializeHistoriskSimulering(): String? = this?.let { simulering ->
    serialize(
        HistoriskSimuleringDbJson(
            grunnlag = HistoriskInfotrygdBeregningDbJson.fromDomain(simulering.grunnlag),
            fraOgMed = simulering.simuleringsperiode.fraOgMed,
            tilOgMed = simulering.simuleringsperiode.tilOgMed,
            simulert = simulering.simulert,
            harEksisterendeSuOppdrag = simulering.harEksisterendeSuOppdrag,
            respons = simulering.respons,
        ),
    )
}

internal fun String?.deserializeHistoriskSimulering(): HistoriskInfotrygdSimulering? =
    this?.let { deserialize<HistoriskSimuleringDbJson>(it).toDomain() }
