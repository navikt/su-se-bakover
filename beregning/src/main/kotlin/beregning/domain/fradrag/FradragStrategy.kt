@file:Suppress("ktlint:standard:function-naming")

package beregning.domain.fradrag

import no.nav.su.se.bakover.common.domain.regelspesifisering.Regelspesifiseringer
import no.nav.su.se.bakover.common.domain.regelspesifisering.RegelspesifisertGrunnlag
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import satser.domain.SatsFactory
import satser.domain.supplerendestønad.FullSupplerendeStønadForMåned
import vilkår.inntekt.domain.grunnlag.BeregnetFradragForMåned
import vilkår.inntekt.domain.grunnlag.Fradrag
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragForMåned
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.inntekt.domain.grunnlag.sum
import vilkår.inntekt.domain.grunnlag.sumEksklusiv
import java.lang.Double.max

sealed interface FradragStrategy {

    fun beregn(fradrag: List<Fradrag>, beregningsperiode: Periode): Map<Måned, BeregnetFradragForMåned>
    fun getEpsFribeløp(måned: Måned): FullSupplerendeStønadForMåned?

    /**
     * Kun ment brukt av de som arver fra denne klassen.
     * TODO jah: Flytt ut av interfacet? Annet?
     */
    fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): List<BeregnetFradragForMåned>

    abstract class Uføre : FradragStrategy {
        override fun beregn(fradrag: List<Fradrag>, beregningsperiode: Periode): Map<Måned, BeregnetFradragForMåned> {
            val periodiserteFradrag = fradrag
                .flatMap { FradragFactory.periodiser(it) }
                .groupBy { it.periode }
            val beregningsperiodeMedFradrag: Map<Måned, List<FradragForMåned>> =
                beregningsperiode.måneder().associateWith { (periodiserteFradrag[it] ?: emptyList()) }

            validate(beregningsperiodeMedFradrag)
            return beregnFradrag(beregningsperiodeMedFradrag).associateBy { it.måned }
        }

        protected open fun validate(fradrag: Map<Måned, List<FradragForMåned>>) {
            require(fradrag.values.all { it.`har nøyaktig en forventet inntekt for bruker`() }) { "Hver måned i beregningsperioden må inneholde nøyaktig ett fradrag for brukers forventede inntekt" }
        }

        protected fun List<BeregnetFradragForMåned>.`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(): List<BeregnetFradragForMåned> {
            return map { `filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(it) }
        }

        private fun `filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`(
            fradrag: BeregnetFradragForMåned,
        ): BeregnetFradragForMåned {
            val arbeidsinntekter =
                fradrag.verdi.filter { it.tilhører == FradragTilhører.BRUKER && it.fradragstype == Fradragstype.Arbeidsinntekt }
            val forventetInntekt =
                fradrag.verdi.filter { it.tilhører == FradragTilhører.BRUKER && it.fradragstype == Fradragstype.ForventetInntekt }

            return if (arbeidsinntekter.sumOf { it.månedsbeløp } > forventetInntekt.sumOf { it.månedsbeløp }) {
                fradrag.verdi.minus(forventetInntekt.toSet())
            } else {
                fradrag.verdi.minus(arbeidsinntekter.toSet())
            }.let {
                fradrag.copy(
                    verdi = it,
                    benyttetRegel = Regelspesifiseringer.REGEL_FRADRAG_MINUS_MINST_ARBEID_OG_FORVENTET.benyttRegelspesifisering(
                        avhengigeRegler = listOf(fradrag.benyttetRegel),
                    ),
                )
            }
        }

        private fun List<FradragForMåned>.`har nøyaktig en forventet inntekt for bruker`() =
            singleOrNull { it.tilhører == FradragTilhører.BRUKER && it.fradragstype == Fradragstype.ForventetInntekt } != null

        data object Enslig : Uføre() {
            override fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): List<BeregnetFradragForMåned> {
                return fradrag.map {
                    BeregnetFradragForMåned(
                        måned = it.key,
                        verdi = it.value.filter { fradrag -> fradrag.tilhører == FradragTilhører.BRUKER },
                        benyttetRegel = RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(),
                    )
                }
                    .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
            }

            override fun getEpsFribeløp(måned: Måned) = null
        }

        data class EpsOver67År(val satsfactory: SatsFactory) : Uføre() {
            override fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): List<BeregnetFradragForMåned> {
                return fradrag.map { (måned, fradrag) ->
                    BeregnetFradragForMåned(
                        måned = måned,
                        verdi = fradrag,
                        benyttetRegel = RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(),
                    )
                }
                    .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                    .`fjern EPS fradrag opp til garantipensjonsnivå`()
            }

            override fun getEpsFribeløp(måned: Måned): FullSupplerendeStønadForMåned.Alder =
                satsfactory.ordinærAlder(måned)

            private fun List<BeregnetFradragForMåned>.`fjern EPS fradrag opp til garantipensjonsnivå`(): List<BeregnetFradragForMåned> {
                return map {
                    `fjern EPS fradrag opp til beløpsgrense`(
                        beløpsgrense = getEpsFribeløp(it.måned),
                        fradrag = it,
                    )
                }
            }
        }

        data class EpsUnder67ÅrOgUførFlyktning(val satsfactory: SatsFactory) : Uføre() {
            override fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): List<BeregnetFradragForMåned> {
                return fradrag.map { (måned, fradrag) ->
                    BeregnetFradragForMåned(
                        måned = måned,
                        verdi = fradrag,
                        benyttetRegel = RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(),
                    )
                }
                    .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                    .`fjern EPS fradrag opp til satsbeløp`()
            }

            override fun getEpsFribeløp(måned: Måned): FullSupplerendeStønadForMåned.Uføre =
                satsfactory.ordinærUføre(måned)

            private fun List<BeregnetFradragForMåned>.`fjern EPS fradrag opp til satsbeløp`(): List<BeregnetFradragForMåned> {
                return map {
                    `fjern EPS fradrag opp til beløpsgrense`(
                        beløpsgrense = getEpsFribeløp(it.måned),
                        fradrag = it,
                    )
                }
            }
        }

        data object EpsUnder67År : Uføre() {
            override fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): List<BeregnetFradragForMåned> =
                fradrag.map { (måned, fradrag) ->
                    BeregnetFradragForMåned(
                        måned = måned,
                        verdi = fradrag,
                        benyttetRegel = RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(),
                    )
                }
                    .`filtrer ut den laveste av brukers arbeidsinntekt og forventet inntekt`()
                    .`slå sammen eps sine fradrag til en og samme type`()

            override fun getEpsFribeløp(måned: Måned) = null

            private fun List<BeregnetFradragForMåned>.`slå sammen eps sine fradrag til en og samme type`(): List<BeregnetFradragForMåned> {
                return map { `slå sammen eps sine fradrag til en og samme type`(it) }
            }

            private fun `slå sammen eps sine fradrag til en og samme type`(
                fradrag: BeregnetFradragForMåned,
            ): BeregnetFradragForMåned {
                val (epsFradrag, søkersFradrag) = fradrag.verdi.partition { it.tilhører == FradragTilhører.EPS }
                return if (epsFradrag.isEmpty()) {
                    søkersFradrag
                } else {
                    val sammenslått = FradragFactory.periodiser(
                        FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.BeregnetFradragEPS,
                            månedsbeløp = epsFradrag.sumOf { it.månedsbeløp },
                            periode = fradrag.måned,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.EPS,
                        ),
                    )
                    søkersFradrag.plus(sammenslått)
                }.let {
                    fradrag.copy(
                        verdi = it,
                    )
                }
            }
        }
    }

    abstract class Alder : FradragStrategy {

        override fun beregn(fradrag: List<Fradrag>, beregningsperiode: Periode): Map<Måned, BeregnetFradragForMåned> {
            val periodiserteFradrag = fradrag
                .flatMap { FradragFactory.periodiser(it) }
                .groupBy { it.periode }
            val beregningsperiodeMedFradrag: Map<Måned, List<FradragForMåned>> =
                beregningsperiode.måneder().associateWith { (periodiserteFradrag[it] ?: emptyList()) }

            return beregnFradrag(beregningsperiodeMedFradrag).associateBy { it.måned }
        }

        data object Enslig : Alder() {
            override fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): List<BeregnetFradragForMåned> {
                return fradrag.map { (måned, fradrag) ->
                    BeregnetFradragForMåned(
                        måned = måned,
                        verdi = fradrag.filter { it.tilhører == FradragTilhører.BRUKER },
                        benyttetRegel = RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(),
                    )
                }
            }

            override fun getEpsFribeløp(måned: Måned) = null
        }

        data class EpsOver67År(val satsfactory: SatsFactory) : Alder() {
            override fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): List<BeregnetFradragForMåned> {
                return fradrag.map { (måned, fradrag) ->
                    BeregnetFradragForMåned(
                        måned = måned,
                        verdi = fradrag,
                        benyttetRegel = RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(),
                    )
                }.`fjern EPS fradrag opp til garantipensjonsnivå`()
            }

            override fun getEpsFribeløp(måned: Måned): FullSupplerendeStønadForMåned.Alder =
                satsfactory.ordinærAlder(måned)

            private fun List<BeregnetFradragForMåned>.`fjern EPS fradrag opp til garantipensjonsnivå`(): List<BeregnetFradragForMåned> {
                return map {
                    `fjern EPS fradrag opp til beløpsgrense`(
                        beløpsgrense = getEpsFribeløp(it.måned),
                        fradrag = it,
                    )
                }
            }
        }

        data class EpsUnder67ÅrOgUførFlyktning(val satsfactory: SatsFactory) : Alder() {
            override fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): List<BeregnetFradragForMåned> {
                return fradrag.map { (måned, fradrag) ->
                    BeregnetFradragForMåned(
                        måned = måned,
                        verdi = fradrag,
                        benyttetRegel = RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(),
                    )
                }.`fjern EPS fradrag opp til satsbeløp`()
            }

            override fun getEpsFribeløp(måned: Måned): FullSupplerendeStønadForMåned.Uføre =
                satsfactory.ordinærUføre(måned)

            private fun List<BeregnetFradragForMåned>.`fjern EPS fradrag opp til satsbeløp`(): List<BeregnetFradragForMåned> {
                return map {
                    `fjern EPS fradrag opp til beløpsgrense`(
                        beløpsgrense = getEpsFribeløp(it.måned),
                        fradrag = it,
                    )
                }
            }
        }

        data object EpsUnder67År : Alder() {
            override fun beregnFradrag(fradrag: Map<Måned, List<FradragForMåned>>): List<BeregnetFradragForMåned> {
                return fradrag.map { (måned, fradrag) ->
                    BeregnetFradragForMåned(
                        måned = måned,
                        verdi = fradrag,
                        benyttetRegel = RegelspesifisertGrunnlag.GRUNNLAG_FRADRAG.benyttGrunnlag(),
                    )
                }.`slå sammen eps sine fradrag til en og samme type`()
            }

            override fun getEpsFribeløp(måned: Måned) = null

            private fun List<BeregnetFradragForMåned>.`slå sammen eps sine fradrag til en og samme type`(): List<BeregnetFradragForMåned> {
                return map { `slå sammen eps sine fradrag til en og samme type`(it) }
            }

            // TODO bjg kan være felles på tvers av uføre og alder?
            private fun `slå sammen eps sine fradrag til en og samme type`(fradragForMåned: BeregnetFradragForMåned): BeregnetFradragForMåned {
                val (epsFradrag, søkersFradrag) = fradragForMåned.verdi.partition { it.tilhører == FradragTilhører.EPS }
                return if (epsFradrag.isEmpty()) {
                    søkersFradrag
                } else {
                    val sammenslått = FradragFactory.periodiser(
                        FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.BeregnetFradragEPS,
                            månedsbeløp = epsFradrag.sumOf { it.månedsbeløp },
                            periode = fradragForMåned.måned,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.EPS,
                        ),
                    )
                    søkersFradrag.plus(sammenslått)
                }.let {
                    fradragForMåned.copy(
                        verdi = it,
                    )
                    // .leggTilbenyttetRegel() TODO bjg regel
                }
            }
        }
    }

    /**
     * Var protected. Kun ment brukt internt av de som arver fra denne klassen.
     * TODO jah: Flytt ut av interface? Annet?
     */
    fun `fjern EPS fradrag opp til beløpsgrense`(
        beløpsgrense: FullSupplerendeStønadForMåned,
        fradrag: BeregnetFradragForMåned,
    ): BeregnetFradragForMåned {
        val (epsFradrag, søkersFradrag) = fradrag.verdi.partition { it.tilhører == FradragTilhører.EPS }

        val sumSosialstønad = epsFradrag.sum(Fradragstype.Sosialstønad)

        val sumUtenSosialstønad = epsFradrag.sumEksklusiv(Fradragstype.Sosialstønad)

        // ekskluder sosialstønad fra summering mot beløpsgrense
        val sumOverstigerBeløpsgrense = max(sumUtenSosialstønad - beløpsgrense.satsForMånedAsDouble, 0.0)

        val ingenFradragEps = sumSosialstønad == 0.0 && sumOverstigerBeløpsgrense == 0.0

        return if (ingenFradragEps) {
            søkersFradrag
        } else {
            søkersFradrag.plus(
                FradragFactory.periodiser(
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.BeregnetFradragEPS,
                        // sosialstønad legges til i tilegg til eventuell sum som overstiger beløpsgrense
                        månedsbeløp = sumOverstigerBeløpsgrense + sumSosialstønad,
                        periode = fradrag.måned,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.EPS,
                    ),
                ),
            )
        }.let {
            // TODO bjg - metode som forenkler dette?
            fradrag.copy(
                verdi = it,
                benyttetRegel = Regelspesifiseringer.REGEL_FRADRAG_EPS_OVER_FRIBELØP.benyttRegelspesifisering(
                    avhengigeRegler = listOf(fradrag.benyttetRegel, beløpsgrense.sats.benyttetRegel),
                ),
            )
        }
    }
}
