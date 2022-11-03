package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning.FradragResponseJson.Companion.toJson
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

internal data class MånedsberegningJson(
    val fraOgMed: String,
    val tilOgMed: String,
    val sats: String,
    val grunnbeløp: Int?, // bare relevant for uføre
    val beløp: Int,
    val fradrag: List<FradragResponseJson>,
    val satsbeløp: Int,
    val epsFribeløp: Double,
    val epsInputFradrag: List<FradragResponseJson>,
    val merknader: List<MerknadJson.BeregningJson>,
)
internal fun Månedsberegning.toJson(epsFribeløp: Double, epsInputFradrag: List<Fradrag>) = MånedsberegningJson(
    fraOgMed = periode.fraOgMed.format(DateTimeFormatter.ISO_DATE),
    tilOgMed = periode.tilOgMed.format(DateTimeFormatter.ISO_DATE),
    sats = getSats().name,
    grunnbeløp = getBenyttetGrunnbeløp(),
    beløp = getSumYtelse(),
    fradrag = getFradrag().toJson(),
    satsbeløp = getSatsbeløp().roundToInt(),
    epsFribeløp = epsFribeløp,
    epsInputFradrag = epsInputFradrag.toJson(),
    merknader = getMerknader().toJson(),
)
