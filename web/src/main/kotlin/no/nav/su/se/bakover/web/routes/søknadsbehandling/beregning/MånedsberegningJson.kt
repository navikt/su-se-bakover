package no.nav.su.se.bakover.web.routes.søknadsbehandling.beregning

import beregning.domain.ForventetInntektSammenligning
import beregning.domain.Månedsberegning
import common.presentation.beregning.FradragResponseJson
import common.presentation.beregning.FradragResponseJson.Companion.toJson
import vilkår.inntekt.domain.grunnlag.Fradrag
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class MånedsberegningJson(
    val fraOgMed: String,
    val tilOgMed: String,
    val sats: String,
    // bare relevant for uføre
    val grunnbeløp: Int?,
    val beløp: Int,
    val fradrag: List<FradragResponseJson>,
    val satsbeløp: Int,
    val epsFribeløp: Double,
    val epsInputFradrag: List<FradragResponseJson>,
    val merknader: List<MerknadJson.BeregningJson>,
    val forventetInntektSammenligning: ForventetInntektSammenligningJson?,
)

data class ForventetInntektSammenligningJson(
    val forventetInntekt: Double,
    val arbeidsinntekterEtterLoven: Double,
    val inntekterISammenligningen: List<FradragResponseJson>,
    val benyttetFradrag: String,
)

fun Månedsberegning.toJson(
    epsFribeløp: Double,
    epsInputFradrag: List<Fradrag>,
    forventetInntektSammenligning: ForventetInntektSammenligning?,
) = MånedsberegningJson(
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
    forventetInntektSammenligning = forventetInntektSammenligning?.let {
        ForventetInntektSammenligningJson(
            forventetInntekt = it.forventetInntekt,
            arbeidsinntekterEtterLoven = it.sumArbeidsinntekterEtterLoven,
            inntekterISammenligningen = it.arbeidsinntekterEtterLoven.toJson(),
            benyttetFradrag = it.benyttetFradrag.name,
        )
    },
)
