package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import beregning.domain.Beregning
import common.presentation.beregning.FradragResponseJson
import common.presentation.beregning.FradragResponseJson.Companion.toJson
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import java.time.format.DateTimeFormatter

data class BeregningJson(
    val id: String,
    val opprettet: String,
    val fraOgMed: String,
    val tilOgMed: String,
    val månedsberegninger: List<MånedsberegningJson> = emptyList(),
    val fradrag: List<FradragResponseJson> = emptyList(),
    val begrunnelse: String?,
)

fun Beregning.toJson(): BeregningJson {
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
