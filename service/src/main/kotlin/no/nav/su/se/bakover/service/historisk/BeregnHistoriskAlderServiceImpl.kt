package no.nav.su.se.bakover.service.historisk

import beregning.domain.BeregningFactory
import beregning.domain.BeregningMedFradragBeregnetMånedsvis
import beregning.domain.BeregningStrategy
import beregning.domain.Beregningsperiode
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.periode.Periode
import satser.domain.SatsFactory
import vilkår.inntekt.domain.grunnlag.Fradrag
import java.time.Clock

class BeregnHistoriskAlderServiceImpl(
    val satsFactory: SatsFactory,
    val clock: Clock,
) {

    fun beregnHistoriskAlder(grunnlag: HistoriskAlderBeregning.Grunnlag): HistoriskAlderBeregning {
        val beregningsperioder = grunnlag.perioder.map {
            Beregningsperiode(
                periode = it.periode,
                strategy = it.utledBeregningsstrategi(satsFactory),
            )
        }
        val beregning = BeregningFactory(clock).ny(
            beregningsperioder = beregningsperioder,
            fradrag = grunnlag.fradrag,
        )
        return HistoriskAlderBeregning(beregning)
    }
}

data class HistoriskAlderBeregning(
    val beregning: BeregningMedFradragBeregnetMånedsvis,
) {
    data class Grunnlag(
        val perioder: List<HistoriskPeriodeMedStrategi>,
        val fradrag: List<Fradrag>,
    )
}

data class HistoriskPeriodeMedStrategi(
    val periode: Periode,
    val strategi: Strategi,
) {
    enum class Strategi {
        BorMedVoksne,
        EpsUnder67År,
        Eps67EllerEldre,
        EpsUnder67ÅrOgUførFlyktning,
        BorAlene,
    }
}

private fun HistoriskPeriodeMedStrategi.utledBeregningsstrategi(
    satsFactory: SatsFactory,
): BeregningStrategy {
    val sakstype = Sakstype.ALDER
    return when (this.strategi) {
        HistoriskPeriodeMedStrategi.Strategi.BorMedVoksne -> BeregningStrategy.BorMedVoksne(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )

        HistoriskPeriodeMedStrategi.Strategi.EpsUnder67År -> BeregningStrategy.EpsUnder67År(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )

        HistoriskPeriodeMedStrategi.Strategi.Eps67EllerEldre -> BeregningStrategy.Eps67EllerEldre(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )

        HistoriskPeriodeMedStrategi.Strategi.EpsUnder67ÅrOgUførFlyktning -> BeregningStrategy.EpsUnder67ÅrOgUførFlyktning(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )

        HistoriskPeriodeMedStrategi.Strategi.BorAlene -> BeregningStrategy.BorAlene(
            satsFactory = satsFactory,
            sakstype = sakstype,
        )
    }
}
