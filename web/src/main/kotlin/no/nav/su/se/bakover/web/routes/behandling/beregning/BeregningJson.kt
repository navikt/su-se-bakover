package no.nav.su.se.bakover.web.routes.behandling.beregning

import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.getEpsFribeløp
import no.nav.su.se.bakover.web.routes.behandling.beregning.FradragJson.Companion.toJson
import java.time.format.DateTimeFormatter

internal data class BeregningJson(
    val id: String,
    val opprettet: String,
    val fraOgMed: String,
    val tilOgMed: String,
    val sats: String,
    val månedsberegninger: List<MånedsberegningJson> = emptyList(),
    val fradrag: List<FradragJson> = emptyList(),
    val begrunnelse: String?
)

internal fun Beregning.toJson(): BeregningJson {
    val epsInputFradragMap = getFradrag()
        .filter { it.getTilhører() == FradragTilhører.EPS }
        .flatMap { FradragFactory.periodiser(it) }
        .groupBy { it.getPeriode() }

    return BeregningJson(
        id = getId().toString(),
        opprettet = getOpprettet().toString(),
        fraOgMed = getPeriode().fraOgMed.format(DateTimeFormatter.ISO_DATE),
        tilOgMed = getPeriode().tilOgMed.format(DateTimeFormatter.ISO_DATE),
        sats = getSats().name,
        månedsberegninger = getMånedsberegninger().map {
            it.toJson(
                getEpsFribeløp(getFradragStrategyName(), it.getPeriode()),
                epsInputFradrag = epsInputFradragMap[it.getPeriode()] ?: emptyList()
            )
        },
        fradrag = getFradrag().toJson(),
        begrunnelse = getBegrunnelse()
    )
}
