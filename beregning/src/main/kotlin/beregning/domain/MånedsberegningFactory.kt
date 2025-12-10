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
     * @param måned måned det skal beregnes for
     * @param strategy strategien som skal benyttes for beregningen
     * @param fradrag en liste med fradrag som skal trekkes fra ytelsen. Kun fradrag som er aktuelle for [måned] tas med i beregningen.
     */
    fun ny(
        måned: Måned,
        strategy: BeregningStrategy,
        fradrag: List<Fradrag>,
    ): BeregningForMånedRegelspesifisert {
        val ytelseFørFradrag = strategy.beregn(måned)
        val satsbeløp: Double = ytelseFørFradrag.satsForMånedAsDouble

        val beregnetFradrag = strategy.beregnFradrag(måned, fradrag, satsbeløp)
        val sumFradrag = beregnetFradrag.sumFradrag

        val sumYtelse: Int = (satsbeløp - sumFradrag)
            .positiveOrZero()
            .roundToInt()

        // TODO fribeløp bergnes på nytt her, men kun for visningsformål. Bør egentlig finne fribeløp som er benyttet i faktisk beregning
        val fribeløpForEps = strategy.beregnFribeløpEPS(måned)
        val verdi = BeregningForMåned(
            måned = måned,
            fullSupplerendeStønadForMåned = ytelseFørFradrag,
            fradrag = beregnetFradrag.fradragForMåned.verdi,
            fribeløpForEps = fribeløpForEps,
            sumYtelse = sumYtelse,
            sumFradrag = sumFradrag,
        )
        return BeregningForMånedRegelspesifisert(
            verdi = verdi,
            benyttetRegel = Regelspesifiseringer.REGEL_SATS_MINUS_FRADRAG_AVRUNDET.benyttRegelspesifisering(
                verdi = verdi.toString(),
                avhengigeRegler = listOf(
                    ytelseFørFradrag.sats.benyttetRegel,
                    beregnetFradrag.benyttetRegel,
                ),
            ),
        )
    }
}
