package beregning.domain

import no.nav.su.se.bakover.common.domain.extensions.positiveOrZero
import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.tid.periode.Måned
import vilkår.inntekt.domain.grunnlag.Fradrag
import kotlin.math.roundToInt

data object MånedsberegningFactory {
    /**
     * Beregner ytelsen for en spesifiskert måned i henhold til angitt strategi.
     *
     * Regelspesifisering: [Regelspesifiseringer.REGEL_MÅNEDSBEREGNING]
     *
     * @param måned måned det skal beregnes for
     * @param strategy strategien som skal benyttes for beregningen
     * @param fradrag en liste med fradrag som skal trekkes fra ytelsen. Kun fradrag som er aktuelle for [måned] tas med i beregningen.
     */
    fun ny(
        måned: Måned,
        strategy: BeregningStrategy,
        fradrag: List<Fradrag>,
    ): BeregningForMåned {
        val ytelseFørFradrag = strategy.beregn(måned)
        val satsbeløp: Double = ytelseFørFradrag.satsForMånedAsDouble

        val beregnetFradrag = strategy.beregnFradrag(måned, fradrag, satsbeløp)
        val sumFradrag = beregnetFradrag.sumFradrag

        val sumYtelse: Int = (satsbeløp - sumFradrag)
            .positiveOrZero()
            .roundToInt()

        val fribeløpForEps = strategy.beregnFribeløpEPS(måned) // TODO ?? må den være her eller kan den hentes fra kilde/bruk?
        return BeregningForMåned(
            måned = måned,
            fullSupplerendeStønadForMåned = ytelseFørFradrag,
            fradrag = beregnetFradrag.fradragForMåned.verdi,
            fribeløpForEps = fribeløpForEps,
            sumYtelse = sumYtelse,
            sumFradrag = sumFradrag,
        ).leggTilbenyttetRegler(
            mutableListOf(
                Regelspesifiseringer.REGEL_MÅNEDSBEREGNING.benyttRegelspesifisering(),
            ) + ytelseFørFradrag.benyttetRegel + beregnetFradrag.benyttetRegel,
        )
    }
}
