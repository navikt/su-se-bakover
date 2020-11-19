package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Minstepensjonsnivå
import no.nav.su.se.bakover.domain.beregning.Sats

internal sealed class FradragStrategy {
    fun beregn(fradrag: List<Fradrag>, beregningsperiode: Periode): Map<Periode, List<Fradrag>> {
        val periodiserteFradrag = fradrag
            .flatMap { it.periodiser() }
            .groupBy { it.getPeriode() }
        val beregningsperiodeMedFradrag = beregningsperiode.tilMånedsperioder()
            .map { it to (periodiserteFradrag[it] ?: emptyList()) }
            .toMap()

        validate(beregningsperiodeMedFradrag)
        return beregnFradrag(beregningsperiodeMedFradrag)
    }

    protected open fun validate(fradrag: Map<Periode, List<Fradrag>>) {
        require(fradrag.values.all { it.`har nøyaktig en forventet inntekt for bruker`() }) { "Hele beregningsperioden må inneholde fradrag for brukers forventede inntekt etter uførhet." }
    }

    protected abstract fun beregnFradrag(fradrag: Map<Periode, List<Fradrag>>): Map<Periode, List<Fradrag>>

    object Enslig : FradragStrategy() {
        override fun beregnFradrag(fradrag: Map<Periode, List<Fradrag>>): Map<Periode, List<Fradrag>> {
            return fradrag.mapValues { it.value.filter { it.getTilhører() == FradragTilhører.BRUKER } }
                .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
        }
    }

    object EpsOver67År : FradragStrategy() {
        override fun beregnFradrag(fradrag: Map<Periode, List<Fradrag>>): Map<Periode, List<Fradrag>> {
            return fradrag
                .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                .`fjern EPS fradrag opp til minstepensjonsnivå`()
        }

        private fun periodisertSumMinstepensjonsnivå(periode: Periode) =
            Minstepensjonsnivå.Ordinær.periodiser(periode).values.sumByDouble { it }

        private fun Map<Periode, List<Fradrag>>.`fjern EPS fradrag opp til minstepensjonsnivå`(): Map<Periode, List<Fradrag>> {
            return mapValues {
                `fjern EPS fradrag opp til beløpsgrense`(
                    periode = it.key,
                    beløpsgrense = periodisertSumMinstepensjonsnivå(it.key),
                    fradrag = it.value
                )
            }
        }
    }

    object EpsUnder67ÅrOgUførFlyktning : FradragStrategy() {
        override fun beregnFradrag(fradrag: Map<Periode, List<Fradrag>>): Map<Periode, List<Fradrag>> {
            return fradrag
                .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                .`fjern EPS fradrag opp til satsbeløp`()
        }

        private fun periodisertSumSatsbeløp(periode: Periode) =
            Sats.ORDINÆR.periodiser(periode).values.sumByDouble { it }

        private fun Map<Periode, List<Fradrag>>.`fjern EPS fradrag opp til satsbeløp`(): Map<Periode, List<Fradrag>> {
            return mapValues {
                `fjern EPS fradrag opp til beløpsgrense`(
                    it.key,
                    periodisertSumSatsbeløp(it.key),
                    it.value
                )
            }
        }
    }

    object EpsUnder67År : FradragStrategy() {
        override fun beregnFradrag(fradrag: Map<Periode, List<Fradrag>>): Map<Periode, List<Fradrag>> =
            fradrag
                .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                .`slå sammen eps sine fradrag til en og samme type`()

        private fun Map<Periode, List<Fradrag>>.`slå sammen eps sine fradrag til en og samme type`(): Map<Periode, List<Fradrag>> {
            return mapValues { `slå sammen eps sine fradrag til en og samme type`(it.key, it.value) }
        }

        private fun `slå sammen eps sine fradrag til en og samme type`(
            periode: Periode,
            fradrag: List<Fradrag>
        ): List<Fradrag> {
            val (epsFradrag, søkersFradrag) = fradrag.partition { it.getTilhører() == FradragTilhører.EPS }
            if (epsFradrag.isEmpty()) return søkersFradrag
            val sammenslått = FradragFactory.ny(
                type = Fradragstype.BeregnetFradragEPS,
                beløp = epsFradrag.sumByDouble { it.getTotaltFradrag() },
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS
            )
            return søkersFradrag.plus(sammenslått)
        }
    }

    protected fun Map<Periode, List<Fradrag>>.`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(): Map<Periode, List<Fradrag>> {
        return mapValues { `filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(it.value) }
    }

    private fun `filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(
        fradrag: List<Fradrag>
    ): List<Fradrag> {
        val arbeidsinntekter =
            fradrag.filter { it.getTilhører() == FradragTilhører.BRUKER && it.getFradragstype() == Fradragstype.Arbeidsinntekt }
        val forventetInntekt =
            fradrag.filter { it.getTilhører() == FradragTilhører.BRUKER && it.getFradragstype() == Fradragstype.ForventetInntekt }

        return if (arbeidsinntekter.sumByDouble { it.getTotaltFradrag() } > forventetInntekt.sumByDouble { it.getTotaltFradrag() })
            fradrag.minus(forventetInntekt)
        else
            fradrag.minus(arbeidsinntekter)
    }

    protected fun `fjern EPS fradrag opp til beløpsgrense`(
        periode: Periode,
        beløpsgrense: Double,
        fradrag: List<Fradrag>
    ): List<Fradrag> {
        val (epsFradrag, søkersFradrag) = fradrag.partition { it.getTilhører() == FradragTilhører.EPS }
        val epsFradragSum = epsFradrag.sumByDouble { it.getTotaltFradrag() }

        val beregnetFradragEps = epsFradragSum - beløpsgrense

        if (beregnetFradragEps <= 0) {
            return søkersFradrag
        }

        return søkersFradrag.plus(
            FradragFactory.ny(
                type = Fradragstype.BeregnetFradragEPS,
                beløp = beregnetFradragEps,
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS
            )
        )
    }

    protected fun List<Fradrag>.`har nøyaktig en forventet inntekt for bruker`() =
        singleOrNull { it.getTilhører() == FradragTilhører.BRUKER && it.getFradragstype() == Fradragstype.ForventetInntekt } != null
}
