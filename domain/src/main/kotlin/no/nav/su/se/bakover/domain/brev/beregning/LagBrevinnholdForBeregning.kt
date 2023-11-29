package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.common.extensions.norwegianLocale
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.beregning.SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import beregning.domain.Beregning as FaktiskBeregning

data class LagBrevinnholdForBeregning(
    private val beregning: FaktiskBeregning,
) {
    internal val brevInnhold: List<Beregningsperiode> =
        SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder(beregning.getMånedsberegninger()).beregningsperioder.map { beregningsperiode ->
            Beregningsperiode(
                periode = beregningsperiode.periode.formaterForBrev(),
                ytelsePerMåned = beregningsperiode.getSumYtelse(),
                satsbeløpPerMåned = beregningsperiode.getSatsbeløp().roundToInt(),
                epsFribeløp = beregningsperiode.getFribeløpForEps().roundToInt(),
                fradrag = Fradrag(
                    bruker = BrukerFradragBenyttetIBeregningsperiode(beregningsperiode.fradrag()).fradrag,
                    eps = finnFradragForEps(beregningsperiode),
                ),
                sats = beregningsperiode.getSats().toString().lowercase(),
            )
        }

    private fun Periode.formaterForBrev() = DateTimeFormatter.ofPattern("LLLL yyyy", norwegianLocale).let {
        BrevPeriode(
            fraOgMed = it.format(this.fraOgMed),
            tilOgMed = it.format(this.tilOgMed),
        )
    }

    /**
     * Deducing fradrag for EPS is a bit more complicated due to the "type erasure" of fradrag.
     * When calculating each month, all input fradrag for EPS will be transformed into a single fradrag of type
     * [no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.BeregnetFradragEPS] if the total sum of fradrag
     * exceedes the "fribeløp" for the specific month - otherwise no fradrag will actually be used for the calculation.
     * To determine the types for fradrag actually used, we need to "backtrack" to the original input fradrag.
     */
    private fun finnFradragForEps(beregningsperiode: SlåSammenEkvivalenteMånedsberegningerTilBeregningsperioder.EkvivalenteMånedsberegninger): Fradrag.Eps {
        // find all input-fradrag that are applicable for the period in question
        val epsFradragFraSaksbehandler = EpsFradragFraSaksbehandlerIBeregningsperiode(
            // found from the original input-fradrag
            beregning.getFradrag(),
            beregningsperiode.periode,
        ).fradrag

        return when (beregningsperiode.erFradragForEpsBenyttetIBeregning()) {
            true -> Fradrag.Eps(
                fradrag = epsFradragFraSaksbehandler,
                // eps fradrag used in calculation excludes this (eps fradrag below fribeløp will not be used in beregning)
                harFradragMedSumSomErLavereEnnFribeløp = false,
            )
            false -> Fradrag.Eps(
                // no fradrag for eps are actually used for the calculation, avoid display of all eps fradrag entirely
                fradrag = emptyList(),
                // fradrag for eps are present, but not included in actual beregning
                harFradragMedSumSomErLavereEnnFribeløp = epsFradragFraSaksbehandler.isNotEmpty(),
            )
        }
    }
}
