package no.nav.su.se.bakover.domain.brev.beregning

import no.nav.su.se.bakover.domain.beregning.GrupperEkvivalenteMånedsberegninger
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragStrategy
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt
import no.nav.su.se.bakover.domain.beregning.Beregning as FaktiskBeregning

data class LagBrevinnholdForBeregning(
    private val beregning: FaktiskBeregning
) {
    internal val brevInnhold: List<Beregningsperiode> =
        GrupperEkvivalenteMånedsberegninger(beregning.getMånedsberegninger()).grupper.map { gruppertMånedsberegning ->
            Beregningsperiode(
                // TODO ikke vis eps fradrag som er under fribeløp
                // TODO eps firbeløp ikke safe vel?
                periode = gruppertMånedsberegning.getPeriode(),
                ytelsePerMåned = gruppertMånedsberegning.getSumYtelse(),
                satsbeløpPerMåned = gruppertMånedsberegning.getSatsbeløp().roundToInt(),
                epsFribeløp = FradragStrategy.fromName(beregning.getFradragStrategyName())
                    .getEpsFribeløp(gruppertMånedsberegning.getPeriode()).let {
                        it / gruppertMånedsberegning.getPeriode().getAntallMåneder()
                    }.roundToTwoDecimals(),
                fradrag = Fradrag(
                    bruker = BrukerFradragForBeregningsperiode(beregning.getFradrag(), gruppertMånedsberegning.getPeriode()).fradrag,
                    eps = EpsFradragForBeregningsperiode(beregning.getFradrag(), gruppertMånedsberegning.getPeriode()).fradrag
                )
            )
        }

}

fun Double.roundToTwoDecimals() =
    BigDecimal(this).setScale(2, RoundingMode.HALF_UP)
        .toDouble()
