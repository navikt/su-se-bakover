@file:Suppress("ktlint:standard:function-naming")

package no.nav.su.se.bakover.domain.beregning.fradrag

import behandling.domain.beregning.fradrag.Fradrag
import behandling.domain.beregning.fradrag.FradragFactory
import behandling.domain.beregning.fradrag.FradragForMåned
import behandling.domain.beregning.fradrag.FradragTilhører
import behandling.domain.beregning.fradrag.Fradragstype
import behandling.domain.beregning.fradrag.sum
import behandling.domain.beregning.fradrag.sumEksklusiv
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import sats.domain.SatsFactory
import java.lang.Double.max
sealed class FradragStrategy {

    abstract fun beregn(fradrag: List<Fradrag>, beregningsperiode: Periode): Map<Måned, List<FradragForMåned>>
    abstract fun getEpsFribeløp(måned: Måned): Double
    protected abstract fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): Map<Måned, List<FradragForMåned>>

    abstract class Uføre : FradragStrategy() {
        override fun beregn(fradrag: List<Fradrag>, beregningsperiode: Periode): Map<Måned, List<FradragForMåned>> {
            val periodiserteFradrag = fradrag
                .flatMap { FradragFactory.periodiser(it) }
                .groupBy { it.periode }
            val beregningsperiodeMedFradrag: Map<Måned, List<FradragForMåned>> =
                beregningsperiode.måneder().associateWith { (periodiserteFradrag[it] ?: emptyList()) }

            validate(beregningsperiodeMedFradrag)
            return beregnFradrag(beregningsperiodeMedFradrag)
        }

        protected open fun validate(fradrag: Map<Måned, List<FradragForMåned>>) {
            require(fradrag.values.all { it.`har nøyaktig en forventet inntekt for bruker`() }) { "Hver måned i beregningsperioden må inneholde nøyaktig ett fradrag for brukers forventede inntekt" }
        }

        protected fun Map<Måned, List<FradragForMåned>>.`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(): Map<Måned, List<FradragForMåned>> {
            return mapValues { `filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(it.value) }
        }

        private fun `filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(
            fradrag: List<FradragForMåned>,
        ): List<FradragForMåned> {
            val arbeidsinntekter =
                fradrag.filter { it.tilhører == FradragTilhører.BRUKER && it.fradragstype == Fradragstype.Arbeidsinntekt }
            val forventetInntekt =
                fradrag.filter { it.tilhører == FradragTilhører.BRUKER && it.fradragstype == Fradragstype.ForventetInntekt }

            return if (arbeidsinntekter.sumOf { it.månedsbeløp } > forventetInntekt.sumOf { it.månedsbeløp }) {
                fradrag.minus(forventetInntekt.toSet())
            } else {
                fradrag.minus(arbeidsinntekter.toSet())
            }
        }

        private fun List<FradragForMåned>.`har nøyaktig en forventet inntekt for bruker`() =
            singleOrNull { it.tilhører == FradragTilhører.BRUKER && it.fradragstype == Fradragstype.ForventetInntekt } != null

        data object Enslig : Uføre() {
            override fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): Map<Måned, List<FradragForMåned>> {
                return fradrag.mapValues { it.value.filter { fradrag -> fradrag.tilhører == FradragTilhører.BRUKER } }
                    .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
            }

            override fun getEpsFribeløp(måned: Måned): Double = 0.0
        }

        data class EpsOver67År(val satsfactory: SatsFactory) : Uføre() {
            override fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): Map<Måned, List<FradragForMåned>> {
                return fradrag
                    .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                    .`fjern EPS fradrag opp til garantipensjonsnivå`()
            }

            override fun getEpsFribeløp(måned: Måned): Double = satsfactory.ordinærAlder(måned).satsForMånedAsDouble

            private fun Map<Måned, List<FradragForMåned>>.`fjern EPS fradrag opp til garantipensjonsnivå`(): Map<Måned, List<FradragForMåned>> {
                return mapValues { (måned, fradrag) ->
                    `fjern EPS fradrag opp til beløpsgrense`(
                        måned = måned,
                        beløpsgrense = getEpsFribeløp(måned),
                        fradrag = fradrag,
                    )
                }
            }
        }

        data class EpsUnder67ÅrOgUførFlyktning(val satsfactory: SatsFactory) : Uføre() {
            override fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): Map<Måned, List<FradragForMåned>> {
                return fradrag
                    .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                    .`fjern EPS fradrag opp til satsbeløp`()
            }

            override fun getEpsFribeløp(måned: Måned): Double = satsfactory.ordinærUføre(måned).satsForMånedAsDouble

            private fun Map<Måned, List<FradragForMåned>>.`fjern EPS fradrag opp til satsbeløp`(): Map<Måned, List<FradragForMåned>> {
                return mapValues { (måned, fradrag) ->
                    `fjern EPS fradrag opp til beløpsgrense`(
                        måned = måned,
                        beløpsgrense = getEpsFribeløp(måned),
                        fradrag = fradrag,
                    )
                }
            }
        }

        data object EpsUnder67År : Uføre() {
            override fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): Map<Måned, List<FradragForMåned>> =
                fradrag
                    .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                    .`slå sammen eps sine fradrag til en og samme type`()

            override fun getEpsFribeløp(måned: Måned): Double = 0.0

            private fun Map<Måned, List<FradragForMåned>>.`slå sammen eps sine fradrag til en og samme type`(): Map<Måned, List<FradragForMåned>> {
                return mapValues { `slå sammen eps sine fradrag til en og samme type`(it.key, it.value) }
            }

            private fun `slå sammen eps sine fradrag til en og samme type`(
                måned: Måned,
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
    }

    abstract class Alder : FradragStrategy() {

        override fun beregn(fradrag: List<Fradrag>, beregningsperiode: Periode): Map<Måned, List<FradragForMåned>> {
            val periodiserteFradrag = fradrag
                .flatMap { FradragFactory.periodiser(it) }
                .groupBy { it.periode }
            val beregningsperiodeMedFradrag: Map<Måned, List<FradragForMåned>> =
                beregningsperiode.måneder().associateWith { (periodiserteFradrag[it] ?: emptyList()) }

            return beregnFradrag(beregningsperiodeMedFradrag)
        }

        data object Enslig : Alder() {
            override fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): Map<Måned, List<FradragForMåned>> {
                return fradrag.mapValues { it.value.filter { fradrag -> fradrag.tilhører == FradragTilhører.BRUKER } }
            }

            override fun getEpsFribeløp(måned: Måned): Double = 0.0
        }

        data class EpsOver67År(val satsfactory: SatsFactory) : Alder() {
            override fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): Map<Måned, List<FradragForMåned>> {
                return fradrag.`fjern EPS fradrag opp til garantipensjonsnivå`()
            }

            override fun getEpsFribeløp(måned: Måned): Double = satsfactory.ordinærAlder(måned).satsForMånedAsDouble

            private fun Map<Måned, List<FradragForMåned>>.`fjern EPS fradrag opp til garantipensjonsnivå`(): Map<Måned, List<FradragForMåned>> {
                return mapValues { (måned, fradrag) ->
                    `fjern EPS fradrag opp til beløpsgrense`(
                        måned = måned,
                        beløpsgrense = getEpsFribeløp(måned),
                        fradrag = fradrag,
                    )
                }
            }
        }

        data class EpsUnder67ÅrOgUførFlyktning(val satsfactory: SatsFactory) : Alder() {
            override fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): Map<Måned, List<FradragForMåned>> {
                return fradrag.`fjern EPS fradrag opp til satsbeløp`()
            }

            override fun getEpsFribeløp(måned: Måned): Double = satsfactory.ordinærUføre(måned).satsForMånedAsDouble

            private fun Map<Måned, List<FradragForMåned>>.`fjern EPS fradrag opp til satsbeløp`(): Map<Måned, List<FradragForMåned>> {
                return mapValues { (måned, fradrag) ->
                    `fjern EPS fradrag opp til beløpsgrense`(
                        måned = måned,
                        beløpsgrense = getEpsFribeløp(måned),
                        fradrag = fradrag,
                    )
                }
            }
        }

        data object EpsUnder67År : Alder() {
            override fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): Map<Måned, List<FradragForMåned>> {
                return fradrag.`slå sammen eps sine fradrag til en og samme type`()
            }

            override fun getEpsFribeløp(måned: Måned): Double = 0.0

            private fun Map<Måned, List<FradragForMåned>>.`slå sammen eps sine fradrag til en og samme type`(): Map<Måned, List<FradragForMåned>> {
                return mapValues { `slå sammen eps sine fradrag til en og samme type`(it.key, it.value) }
            }

            private fun `slå sammen eps sine fradrag til en og samme type`(
                måned: Måned,
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
    }

    protected fun `fjern EPS fradrag opp til beløpsgrense`(
        måned: Måned,
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
}
