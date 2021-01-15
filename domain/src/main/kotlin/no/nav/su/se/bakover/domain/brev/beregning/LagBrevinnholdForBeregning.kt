package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.common.roundToDecimals
import no.nav.su.se.bakover.domain.beregning.GrupperEkvivalenteMånedsberegninger
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import kotlin.math.roundToInt
import no.nav.su.se.bakover.domain.beregning.Beregning as FaktiskBeregning

data class LagBrevinnholdForBeregning(
    private val beregning: FaktiskBeregning
) {
    internal val brevInnhold: List<Beregningsperiode> =
        GrupperEkvivalenteMånedsberegninger(beregning.getMånedsberegninger()).grupper.map { beregningsperiode ->
            val inneholderEpsFradrag = beregningsperiode.getFradrag().innholderBeregnetFradragEps()
            val epz = EpsFradragForBeregningsperiode(
                beregning.getFradrag(),
                beregningsperiode.getPeriode()
            ).fradrag
            Beregningsperiode(
                // TODO ikke vis eps fradrag som er under fribeløp
                // TODO eps firbeløp ikke safe vel?
                periode = beregningsperiode.getPeriode(),
                ytelsePerMåned = beregningsperiode.getSumYtelse(),
                satsbeløpPerMåned = beregningsperiode.getSatsbeløp().roundToInt(),
                epsFribeløp = FradragStrategy.fromName(beregning.getFradragStrategyName())
                    .getEpsFribeløp(beregningsperiode.getPeriode()).let {
                        it / beregningsperiode.getPeriode().getAntallMåneder()
                    }.roundToDecimals(2),
                fradrag = Fradrag(
                    bruker = BrukerFradragForBeregningsperiode(beregningsperiode.getFradrag()).fradrag,
                    eps = Fradrag.Eps(
                        fradrag = when (inneholderEpsFradrag) {
                            true -> epz
                            false -> emptyList()
                        },
                        harFradragMedSumSomErLavereEnnFribeløp = !inneholderEpsFradrag && epz.isNotEmpty()
                    )
                )
            )
        }

    private fun List<no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag>.innholderBeregnetFradragEps() =
        this.any { it.getFradragstype() == Fradragstype.BeregnetFradragEPS }
}
