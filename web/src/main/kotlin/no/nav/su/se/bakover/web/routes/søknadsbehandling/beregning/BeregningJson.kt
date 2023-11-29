package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import beregning.domain.fradrag.FradragFactory
import beregning.domain.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragResponseJson.Companion.toJson
import java.time.format.DateTimeFormatter

internal data class BeregningJson(
    val id: String,
    val opprettet: String,
    val fraOgMed: String,
    val tilOgMed: String,
    val månedsberegninger: List<MånedsberegningJson> = emptyList(),
    val fradrag: List<FradragResponseJson> = emptyList(),
    val begrunnelse: String?,
)

internal fun Beregning.toJson(): BeregningJson {
    val epsInputFradragMap = getFradrag()
        .filter { it.tilhører == FradragTilhører.EPS }
        .flatMap { FradragFactory.periodiser(it) }
        .groupBy { it.periode }

    return BeregningJson(
        id = getId().toString(),
        opprettet = getOpprettet().toString(),
        fraOgMed = periode.fraOgMed.format(DateTimeFormatter.ISO_DATE),
        tilOgMed = periode.tilOgMed.format(DateTimeFormatter.ISO_DATE),
        månedsberegninger = getMånedsberegninger().map {
            it.toJson(
                it.getFribeløpForEps(),
                epsInputFradrag = epsInputFradragMap[it.periode] ?: emptyList(),
            )
        },
        fradrag = getFradrag().toJson(),
        begrunnelse = getBegrunnelse(),
    )
}
