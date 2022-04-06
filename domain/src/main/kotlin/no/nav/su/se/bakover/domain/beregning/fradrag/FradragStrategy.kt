package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Månedsperiode
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.satser.FullSupplerendeStønadFactory
import no.nav.su.se.bakover.domain.satser.Garantipensjonsnivå
import java.lang.Double.max

enum class FradragStrategyName {
    Enslig,
    EpsOver67År,
    EpsUnder67ÅrOgUførFlyktning,
    EpsUnder67År
}

sealed class FradragStrategy(private val name: FradragStrategyName) {

    fun getName() = name

    fun beregn(fradrag: List<Fradrag>, beregningsperiode: Periode): Map<Månedsperiode, List<FradragForMåned>> {
        val periodiserteFradrag = fradrag
            .flatMap { FradragFactory.periodiser(it) }
            .groupBy { it.periode }
        val beregningsperiodeMedFradrag: Map<Månedsperiode, List<FradragForMåned>> =
            beregningsperiode.tilMånedsperioder().associateWith { (periodiserteFradrag[it] ?: emptyList()) }

        validate(beregningsperiodeMedFradrag)
        return beregnFradrag(beregningsperiodeMedFradrag)
    }

    protected open fun validate(fradrag: Map<Månedsperiode, List<FradragForMåned>>) {
        require(fradrag.values.all { it.`har nøyaktig en forventet inntekt for bruker`() }) { "Hver måned i beregningsperioden må inneholde nøyaktig ett fradrag for brukers forventede inntekt" }
    }

    protected abstract fun beregnFradrag(fradrag: Map<Månedsperiode, List<FradragForMåned>>): Map<Månedsperiode, List<FradragForMåned>>

    abstract fun getEpsFribeløp(måned: Månedsperiode): Double

    object Enslig : FradragStrategy(FradragStrategyName.Enslig) {
        override fun beregnFradrag(fradrag: Map<Månedsperiode, List<FradragForMåned>>): Map<Månedsperiode, List<FradragForMåned>> {
            return fradrag.mapValues { it.value.filter { fradrag -> fradrag.tilhører == FradragTilhører.BRUKER } }
                .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
        }

        override fun getEpsFribeløp(måned: Månedsperiode): Double = 0.0
    }

    object EpsOver67År : FradragStrategy(FradragStrategyName.EpsOver67År) {
        override fun beregnFradrag(fradrag: Map<Månedsperiode, List<FradragForMåned>>): Map<Månedsperiode, List<FradragForMåned>> {
            return fradrag
                .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                .`fjern EPS fradrag opp til garantipensjonsnivå`()
        }

        override fun getEpsFribeløp(måned: Månedsperiode): Double = periodisertSumGarantipensjonsnivå(måned)

        private fun periodisertSumGarantipensjonsnivå(måned: Månedsperiode) =
            Garantipensjonsnivå.Ordinær.periodiser(måned).values.sumOf { it }

        private fun Map<Månedsperiode, List<FradragForMåned>>.`fjern EPS fradrag opp til garantipensjonsnivå`(): Map<Månedsperiode, List<FradragForMåned>> {
            return mapValues {
                `fjern EPS fradrag opp til beløpsgrense`(
                    måned = it.key,
                    beløpsgrense = periodisertSumGarantipensjonsnivå(it.key),
                    fradrag = it.value,
                )
            }
        }
    }

    data class EpsUnder67ÅrOgUførFlyktning(val fullSupplerendeStønadFactoryOrdinær: FullSupplerendeStønadFactory.Ordinær) :
        FradragStrategy(FradragStrategyName.EpsUnder67ÅrOgUførFlyktning) {
        override fun beregnFradrag(fradrag: Map<Månedsperiode, List<FradragForMåned>>): Map<Månedsperiode, List<FradragForMåned>> {
            return fradrag
                .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                .`fjern EPS fradrag opp til satsbeløp`()
        }

        override fun getEpsFribeløp(måned: Månedsperiode): Double = fullSupplerendeStønadFactoryOrdinær.forMånedsperiode(måned).satsForMånedAsDouble

        private fun Map<Månedsperiode, List<FradragForMåned>>.`fjern EPS fradrag opp til satsbeløp`(): Map<Månedsperiode, List<FradragForMåned>> {
            return mapValues {
                `fjern EPS fradrag opp til beløpsgrense`(
                    måned = it.key,
                    beløpsgrense = getEpsFribeløp(it.key),
                    fradrag = it.value,
                )
            }
        }
    }

    object EpsUnder67År : FradragStrategy(FradragStrategyName.EpsUnder67År) {
        override fun beregnFradrag(fradrag: Map<Månedsperiode, List<FradragForMåned>>): Map<Månedsperiode, List<FradragForMåned>> =
            fradrag
                .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                .`slå sammen eps sine fradrag til en og samme type`()

        override fun getEpsFribeløp(måned: Månedsperiode): Double = 0.0

        private fun Map<Månedsperiode, List<FradragForMåned>>.`slå sammen eps sine fradrag til en og samme type`(): Map<Månedsperiode, List<FradragForMåned>> {
            return mapValues { `slå sammen eps sine fradrag til en og samme type`(it.key, it.value) }
        }

        private fun `slå sammen eps sine fradrag til en og samme type`(
            måned: Månedsperiode,
            fradrag: List<FradragForMåned>,
        ): List<FradragForMåned> {
            val (epsFradrag, søkersFradrag) = fradrag.partition { it.tilhører == FradragTilhører.EPS }
            if (epsFradrag.isEmpty()) return søkersFradrag
            val sammenslått = FradragFactory.periodiser(
                FradragFactory.nyFradragsperiode(
                    fradragstype = Fradragstype.BeregnetFradragEPS,
                    månedsbeløp = epsFradrag.sumOf { it.månedsbeløp },
                    periode = måned,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            )
            return søkersFradrag.plus(sammenslått)
        }
    }

    protected fun Map<Månedsperiode, List<FradragForMåned>>.`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(): Map<Månedsperiode, List<FradragForMåned>> {
        return mapValues { `filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(it.value) }
    }

    private fun `filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(
        fradrag: List<FradragForMåned>,
    ): List<FradragForMåned> {
        val arbeidsinntekter =
            fradrag.filter { it.tilhører == FradragTilhører.BRUKER && it.fradragstype == Fradragstype.Arbeidsinntekt }
        val forventetInntekt =
            fradrag.filter { it.tilhører == FradragTilhører.BRUKER && it.fradragstype == Fradragstype.ForventetInntekt }

        return if (arbeidsinntekter.sumOf { it.månedsbeløp } > forventetInntekt.sumOf { it.månedsbeløp })
            fradrag.minus(forventetInntekt.toSet())
        else
            fradrag.minus(arbeidsinntekter.toSet())
    }

    protected fun `fjern EPS fradrag opp til beløpsgrense`(
        måned: Månedsperiode,
        beløpsgrense: Double,
        fradrag: List<FradragForMåned>,
    ): List<FradragForMåned> {
        val (epsFradrag, søkersFradrag) = fradrag.partition { it.tilhører == FradragTilhører.EPS }

        val sumSosialstønad = epsFradrag.sum(Fradragstype.Sosialstønad)

        val sumUtenSosialstønad = epsFradrag.sumEksklusiv(Fradragstype.Sosialstønad)

        // ekskluder sosialstønad fra summering mot beløpsgrense
        val sumOverstigerBeløpsgrense = max(sumUtenSosialstønad - beløpsgrense, 0.0)

        val ingenFradragEps = sumSosialstønad == 0.0 && sumOverstigerBeløpsgrense == 0.0

        return if (ingenFradragEps) {
            return søkersFradrag
        } else {
            søkersFradrag.plus(
                FradragFactory.periodiser(
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.BeregnetFradragEPS,
                        // sosialstønad legges til i tilegg til eventuell sum som overstiger beløpsgrense
                        månedsbeløp = sumOverstigerBeløpsgrense + sumSosialstønad,
                        periode = måned,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.EPS,
                    ),
                ),
            )
        }
    }

    private fun List<FradragForMåned>.`har nøyaktig en forventet inntekt for bruker`() =
        singleOrNull { it.tilhører == FradragTilhører.BRUKER && it.fradragstype == Fradragstype.ForventetInntekt } != null
}
