package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.common.roundToDecimals
import no.nav.su.se.bakover.domain.beregning.GrupperEkvivalenteMånedsberegninger
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import kotlin.math.roundToInt
import no.nav.su.se.bakover.domain.beregning.Beregning as FaktiskBeregning

data class LagBrevinnholdForBeregning(
    private val beregning: FaktiskBeregning
) {
    internal val brevInnhold: List<Beregningsperiode> =
        GrupperEkvivalenteMånedsberegninger(beregning.getMånedsberegninger()).grupper.map { beregningsperiode ->
            Beregningsperiode(
                // TODO eps firbeløp ikke safe vel?
                periode = beregningsperiode.getPeriode(),
                ytelsePerMåned = beregningsperiode.getSumYtelse(),
                satsbeløpPerMåned = beregningsperiode.getSatsbeløp().roundToInt(),
                epsFribeløp = FradragStrategy.fromName(beregning.getFradragStrategyName())
                    .getEpsFribeløp(beregningsperiode.getPeriode()).let {
                        it / beregningsperiode.getPeriode().getAntallMåneder()
                    }.roundToDecimals(2),
                fradrag = Fradrag(
                    bruker = BrukerFradragBenyttetIBeregningsperiode(beregningsperiode.getFradrag()).fradrag,
                    eps = finnFradragForEps(beregningsperiode)
                )
            )
        }

    /**
     * Deducing fradrag for EPS is a bit more complicated due to the "type erasure" of fradrag.
     * When calculating each month, all input fradrag for EPS will be transformed into a single fradrag of type
     * [no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype.BeregnetFradragEPS] if the total sum of fradrag
     * exceedes the "fribeløp" for the specific month - otherwise no fradrag will actually be used for the calculation.
     * To determine the types for fradrag actually used, we need to "backtrack" to the original input fradrag.
     */
    private fun finnFradragForEps(beregningsperiode: GrupperEkvivalenteMånedsberegninger.GrupperteMånedsberegninger): Fradrag.Eps {
        // find all input-fradrag that are applicable for the period in question
        val epsFradragFraSaksbehandler = EpsFradragFraSaksbehandlerIBeregningsperiode(
            beregning.getFradrag(), // found from the original input-fradrag
            beregningsperiode.getPeriode()
        ).fradrag

        return when (beregningsperiode.erFradragForEpsBenyttetIBeregning()) {
            true -> Fradrag.Eps(
                fradrag = epsFradragFraSaksbehandler,
                harFradragMedSumSomErLavereEnnFribeløp = false // eps fradrag used in calculation excludes this (eps fradrag below fribeløp will not be used in beregning)
            )
            false -> Fradrag.Eps(
                fradrag = emptyList(), // no fradrag for eps are actually used for the calculation, avoid display of all eps fradrag entirely
                harFradragMedSumSomErLavereEnnFribeløp = epsFradragFraSaksbehandler.isNotEmpty() // fradrag for eps are present, but not included in actual beregning
            )
        }
    }
}
