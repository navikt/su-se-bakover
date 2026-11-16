package beregning.domain

import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifisering
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.tid.periode.Måned
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype

/**
 * Sammenligningen bak [Regelspesifiseringer.REGEL_FRADRAG_MINUS_MINST_ARBEID_OG_FORVENTET] for én måned:
 * søkers forventede inntekt etter uførhet mot søkers arbeidsinntekter etter loven.
 * Bare det høyeste beløpet trekkes fra. Ved like beløp trekkes forventet inntekt fra.
 *
 * Utledes fra lagret beregning for visning og lagres ikke.
 */
data class ForventetInntektSammenligning(
    val forventetInntekt: Double,
    val arbeidsinntekterEtterLoven: List<FradragForMåned>,
    val benyttetFradrag: BenyttetFradrag,
) {
    val sumArbeidsinntekterEtterLoven: Double = arbeidsinntekterEtterLoven.sumOf { it.månedsbeløp }

    enum class BenyttetFradrag {
        FORVENTET_INNTEKT,
        ARBEIDSINNTEKTER,
    }
}

/**
 * Gir null når måneden ikke er beregnet med gjeldende versjon av inntektsvalget. Det gjelder alder,
 * beregninger uten regelspesifisering og eldre regelversjoner, som sammenlignet med andre inntektstyper.
 * Gir også null når søkers forventede inntekt ikke finnes entydig i grunnlaget for måneden.
 * Sammenligningen vises bare når forventet inntekt og summen av arbeidsinntekter etter loven er positive.
 */
fun Beregning.forventetInntektSammenligning(måned: Måned): ForventetInntektSammenligning? {
    val månedsberegning = getMånedsberegningerMedRegel().singleOrNull { it.verdi.måned == måned } ?: return null
    val inntektsvalg = Regelspesifiseringer.REGEL_FRADRAG_MINUS_MINST_ARBEID_OG_FORVENTET
    if (månedsberegning.benyttetRegel.versjonerAv(inntektsvalg) != setOf(inntektsvalg.versjon)) return null

    val søkersFradragForMåned = getFradrag()
        .filter { it.tilhører == FradragTilhører.BRUKER }
        .flatMap { FradragFactory.periodiser(it) }
        .filter { it.måned == måned }
    val forventetInntektEtterUførhet = søkersFradragForMåned
        .singleOrNull { it.fradragstype == Fradragstype.ForventetInntekt }
        ?: return null
    val arbeidsinntekterEtterLoven = søkersFradragForMåned.filter { it.fradragstype.erArbeidsinntektEtterloven }
    val sumArbeidsinntekterEtterLoven = arbeidsinntekterEtterLoven.sumOf { it.månedsbeløp }
    if (forventetInntektEtterUførhet.månedsbeløp <= 0.0 || sumArbeidsinntekterEtterLoven <= 0.0) return null

    val forventetInntektEtterUførhetErBenyttet = månedsberegning.verdi.getFradrag().any {
        it.tilhører == FradragTilhører.BRUKER && it.fradragstype == Fradragstype.ForventetInntekt
    }

    return ForventetInntektSammenligning(
        forventetInntekt = forventetInntektEtterUførhet.månedsbeløp,
        arbeidsinntekterEtterLoven = arbeidsinntekterEtterLoven,
        benyttetFradrag = if (forventetInntektEtterUførhetErBenyttet) {
            ForventetInntektSammenligning.BenyttetFradrag.FORVENTET_INNTEKT
        } else {
            ForventetInntektSammenligning.BenyttetFradrag.ARBEIDSINNTEKTER
        },
    )
}

private fun Regelspesifisering.versjonerAv(regel: Regelspesifiseringer): Set<String> = when (this) {
    is Regelspesifisering.Beregning ->
        avhengigeRegler.flatMap { it.versjonerAv(regel) }.toSet() +
            listOfNotNull(versjon.takeIf { kode == regel.kode })

    is Regelspesifisering.Grunnlag,
    Regelspesifisering.BeregnetUtenSpesifisering,
    -> emptySet()
}
