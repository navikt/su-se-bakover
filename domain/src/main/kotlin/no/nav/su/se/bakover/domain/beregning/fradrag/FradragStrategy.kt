package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Månedsperiode
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Garantipensjonsnivå
import no.nav.su.se.bakover.domain.beregning.Sats
import java.lang.Double.max

enum class FradragStrategyName {
    Enslig,
    EpsOver67År,
    EpsUnder67ÅrOgUførFlyktning,
    EpsUnder67År
}

sealed class FradragStrategy(private val name: FradragStrategyName) {
    fun getName() = name

    fun beregn(fradrag: List<Fradrag>, beregningsperiode: Periode): Map<Månedsperiode, List<Fradrag>> {
        val periodiserteFradrag = fradrag
            .flatMap { FradragFactory.periodiser(it) }
            .groupBy { it.periode }
        val beregningsperiodeMedFradrag =
            beregningsperiode.tilMånedsperioder().associateWith { (periodiserteFradrag[it] ?: emptyList()) }

        validate(beregningsperiodeMedFradrag)
        return beregnFradrag(beregningsperiodeMedFradrag)
    }

    protected open fun validate(fradrag: Map<Månedsperiode, List<Fradrag>>) {
        require(fradrag.values.all { it.`har nøyaktig en forventet inntekt for bruker`() }) { "Hver måned i beregningsperioden må inneholde nøyaktig ett fradrag for brukers forventede inntekt" }
    }

    protected abstract fun beregnFradrag(fradrag: Map<Månedsperiode, List<Fradrag>>): Map<Månedsperiode, List<Fradrag>>

    abstract fun getEpsFribeløp(periode: Periode): Double

    object Enslig : FradragStrategy(FradragStrategyName.Enslig) {
        override fun beregnFradrag(fradrag: Map<Månedsperiode, List<Fradrag>>): Map<Månedsperiode, List<Fradrag>> {
            return fradrag.mapValues { it.value.filter { fradrag -> fradrag.tilhører == FradragTilhører.BRUKER } }
                .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
        }

        override fun getEpsFribeløp(periode: Periode): Double = 0.0
    }

    object EpsOver67År : FradragStrategy(FradragStrategyName.EpsOver67År) {
        override fun beregnFradrag(fradrag: Map<Månedsperiode, List<Fradrag>>): Map<Månedsperiode, List<Fradrag>> {
            return fradrag
                .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                .`fjern EPS fradrag opp til garantipensjonsnivå`()
        }

        override fun getEpsFribeløp(periode: Periode): Double = periodisertSumGarantipensjonsnivå(periode)

        private fun periodisertSumGarantipensjonsnivå(periode: Periode) =
            Garantipensjonsnivå.Ordinær.periodiser(periode).values.sumOf { it }

        private fun Map<Månedsperiode, List<Fradrag>>.`fjern EPS fradrag opp til garantipensjonsnivå`(): Map<Månedsperiode, List<Fradrag>> {
            return mapValues {
                `fjern EPS fradrag opp til beløpsgrense`(
                    periode = it.key,
                    beløpsgrense = periodisertSumGarantipensjonsnivå(it.key),
                    fradrag = it.value,
                )
            }
        }
    }

    object EpsUnder67ÅrOgUførFlyktning : FradragStrategy(FradragStrategyName.EpsUnder67ÅrOgUførFlyktning) {
        override fun beregnFradrag(fradrag: Map<Månedsperiode, List<Fradrag>>): Map<Månedsperiode, List<Fradrag>> {
            return fradrag
                .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                .`fjern EPS fradrag opp til satsbeløp`()
        }

        override fun getEpsFribeløp(periode: Periode): Double = periodisertSumSatsbeløp(periode)

        private fun periodisertSumSatsbeløp(periode: Periode) =
            Sats.ORDINÆR.periodiser(periode).values.sumOf { it }

        private fun Map<Månedsperiode, List<Fradrag>>.`fjern EPS fradrag opp til satsbeløp`(): Map<Månedsperiode, List<Fradrag>> {
            return mapValues {
                `fjern EPS fradrag opp til beløpsgrense`(
                    it.key,
                    periodisertSumSatsbeløp(it.key),
                    it.value,
                )
            }
        }
    }

    object EpsUnder67År : FradragStrategy(FradragStrategyName.EpsUnder67År) {
        override fun beregnFradrag(fradrag: Map<Månedsperiode, List<Fradrag>>): Map<Månedsperiode, List<Fradrag>> =
            fradrag
                .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                .`slå sammen eps sine fradrag til en og samme type`()

        override fun getEpsFribeløp(periode: Periode): Double = 0.0

        private fun Map<Månedsperiode, List<Fradrag>>.`slå sammen eps sine fradrag til en og samme type`(): Map<Månedsperiode, List<Fradrag>> {
            return mapValues { `slå sammen eps sine fradrag til en og samme type`(it.key, it.value) }
        }

        private fun `slå sammen eps sine fradrag til en og samme type`(
            periode: Månedsperiode,
            fradrag: List<Fradrag>,
        ): List<Fradrag> {
            val (epsFradrag, søkersFradrag) = fradrag.partition { it.tilhører == FradragTilhører.EPS }
            if (epsFradrag.isEmpty()) return søkersFradrag
            val sammenslått = FradragFactory.periodiser(
                FradragFactory.ny(
                    fradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.BeregnetFradragEPS),
                    månedsbeløp = epsFradrag.sumOf { it.månedsbeløp },
                    periode = periode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.EPS,
                ),
            )
            return søkersFradrag.plus(sammenslått)
        }
    }

    protected fun Map<Månedsperiode, List<Fradrag>>.`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(): Map<Månedsperiode, List<Fradrag>> {
        return mapValues { `filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(it.value) }
    }

    private fun `filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(
        fradrag: List<Fradrag>,
    ): List<Fradrag> {
        val arbeidsinntekter =
            fradrag.filter { it.tilhører == FradragTilhører.BRUKER && it.fradragskategoriWrapper.kategori == Fradragskategori.Arbeidsinntekt }
        val forventetInntekt =
            fradrag.filter { it.tilhører == FradragTilhører.BRUKER && it.fradragskategoriWrapper.kategori == Fradragskategori.ForventetInntekt }

        return if (arbeidsinntekter.sumOf { it.månedsbeløp } > forventetInntekt.sumOf { it.månedsbeløp })
            fradrag.minus(forventetInntekt.toSet())
        else
            fradrag.minus(arbeidsinntekter.toSet())
    }

    protected fun `fjern EPS fradrag opp til beløpsgrense`(
        periode: Periode,
        beløpsgrense: Double,
        fradrag: List<Fradrag>,
    ): List<Fradrag> {
        val (epsFradrag, søkersFradrag) = fradrag.partition { it.tilhører == FradragTilhører.EPS }

        val sumSosialstønad = epsFradrag.sum(FradragskategoriWrapper(Fradragskategori.Sosialstønad))

        val sumUtenSosialstønad = epsFradrag.sumEksklusiv(FradragskategoriWrapper(Fradragskategori.Sosialstønad))

        // ekskluder sosialstønad fra summering mot beløpsgrense
        val sumOverstigerBeløpsgrense = max(sumUtenSosialstønad - beløpsgrense, 0.0)

        val ingenFradragEps = sumSosialstønad == 0.0 && sumOverstigerBeløpsgrense == 0.0

        return if (ingenFradragEps) {
            return søkersFradrag
        } else {
            søkersFradrag.plus(
                FradragFactory.periodiser(
                    FradragFactory.ny(
                        fradragskategoriWrapper = FradragskategoriWrapper(Fradragskategori.BeregnetFradragEPS),
                        // sosialstønad legges til i tilegg til eventuell sum som overstiger beløpsgrense
                        månedsbeløp = sumOverstigerBeløpsgrense + sumSosialstønad,
                        periode = periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.EPS,
                    ),
                ),
            )
        }
    }

    private fun List<Fradrag>.`har nøyaktig en forventet inntekt for bruker`() =
        singleOrNull { it.tilhører == FradragTilhører.BRUKER && it.fradragskategoriWrapper == FradragskategoriWrapper(Fradragskategori.ForventetInntekt) } != null

    companion object {
        fun fromName(name: FradragStrategyName) =
            when (name) {
                FradragStrategyName.Enslig -> Enslig
                FradragStrategyName.EpsOver67År -> EpsOver67År
                FradragStrategyName.EpsUnder67ÅrOgUførFlyktning -> EpsUnder67ÅrOgUførFlyktning
                FradragStrategyName.EpsUnder67År -> EpsUnder67År
            }
    }
}
