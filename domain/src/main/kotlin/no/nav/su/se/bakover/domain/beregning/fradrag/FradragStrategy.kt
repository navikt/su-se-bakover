package no.nav.su.se.bakover.domain.beregning.fradrag

import arrow.syntax.function.pipe
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Minstepensjonsnivå
import no.nav.su.se.bakover.domain.beregning.Sats

internal sealed class FradragStrategy {
    fun beregn(fradrag: List<Fradrag>): List<Fradrag> {
        validate(fradrag)
        return beregnFradrag(fradrag)
    }

    protected open fun validate(fradrag: List<Fradrag>) {
        require(fradrag.harNøyaktigEnForventetInntektFor(FradragTilhører.BRUKER)) { "Fradrag må inneholde brukers forventede inntekt etter uførhet." }
    }

    protected abstract fun beregnFradrag(fradrag: List<Fradrag>): List<Fradrag>

    object Enslig : FradragStrategy() {
        override fun beregnFradrag(fradrag: List<Fradrag>): List<Fradrag> {
            return fradrag
                .filter { it.getTilhører() == FradragTilhører.BRUKER }
                .pipe { `filtrer ut den laveste av arbeidsinntekt og forventet inntekt`(FradragTilhører.BRUKER, it) }
        }
    }

    object EpsOver67År : FradragStrategy() {
        override fun beregnFradrag(fradrag: List<Fradrag>): List<Fradrag> {
            val periode = finnFradragsperiode(fradrag)
            return fradrag
                .pipe { `filtrer ut den laveste av arbeidsinntekt og forventet inntekt`(FradragTilhører.BRUKER, it) }
                .pipe { `fjern EPS fradrag opp til beløpsgrense`(periodisertSumMinstepensjonsnivå(periode), it) }
        }

        private fun periodisertSumMinstepensjonsnivå(periode: Periode) =
            Minstepensjonsnivå.Ordinær.periodiser(periode).values.sumByDouble { it }
    }

    object EpsUnder67ÅrOgUførFlyktning : FradragStrategy() {

        override fun validate(fradrag: List<Fradrag>) {
            super.validate(fradrag)
            require(fradrag.harNøyaktigEnForventetInntektFor(FradragTilhører.EPS)) { "Fradrag må inneholde EPSs forventede inntekt etter uførhet." }
        }

        override fun beregnFradrag(fradrag: List<Fradrag>): List<Fradrag> {
            val periode = finnFradragsperiode(fradrag)
            return fradrag
                .pipe { `filtrer ut den laveste av arbeidsinntekt og forventet inntekt`(FradragTilhører.BRUKER, it) }
                .pipe { `filtrer ut den laveste av arbeidsinntekt og forventet inntekt`(FradragTilhører.EPS, it) }
                .pipe { `fjern EPS fradrag opp til beløpsgrense`(periodisertSumSatsbeløp(periode), it) }
        }

        private fun periodisertSumSatsbeløp(periode: Periode) =
            Sats.ORDINÆR.periodiser(periode).values.sumByDouble { it }
    }

    object EpsUnder67År : FradragStrategy() {
        override fun beregnFradrag(fradrag: List<Fradrag>): List<Fradrag> =
            fradrag
                .pipe {
                    `filtrer ut den laveste av arbeidsinntekt og forventet inntekt`(
                        FradragTilhører.BRUKER,
                        fradrag
                    )
                }
                .pipe { slåSammenEpsFradragTilSammeType(it) }

        private fun slåSammenEpsFradragTilSammeType(fradrag: List<Fradrag>): List<Fradrag> {
            val (epsFradrag, søkersFradrag) = fradrag.partition { it.getTilhører() == FradragTilhører.EPS }
            if (epsFradrag.isEmpty()) return søkersFradrag
            val sammenslått = FradragFactory.ny(
                type = Fradragstype.BeregnetFradragEPS,
                beløp = epsFradrag.sumByDouble { it.getTotaltFradrag() },
                periode = finnFradragsperiode(epsFradrag),
                utenlandskInntekt = null,
                tilhører = FradragTilhører.EPS
            )
            return søkersFradrag.plus(sammenslått)
        }
    }

    protected fun `filtrer ut den laveste av arbeidsinntekt og forventet inntekt`(
        tilhører: FradragTilhører,
        fradrag: List<Fradrag>
    ): List<Fradrag> {
        val arbeidsinntekter =
            fradrag.filter { it.getTilhører() == tilhører && it.getFradragstype() == Fradragstype.Arbeidsinntekt }
        val forventetInntekt =
            fradrag.filter { it.getTilhører() == tilhører && it.getFradragstype() == Fradragstype.ForventetInntekt }

        return if (arbeidsinntekter.sumByDouble { it.getTotaltFradrag() } > forventetInntekt.sumByDouble { it.getTotaltFradrag() })
            fradrag.filter { !forventetInntekt.contains(it) }
        else
            fradrag.filter { !arbeidsinntekter.contains(it) }
    }

    protected fun `fjern EPS fradrag opp til beløpsgrense`(
        beløpsgrense: Double,
        fradrag: List<Fradrag>
    ): List<Fradrag> {
        val (epsFradrag, søkersFradrag) = fradrag.partition { it.getTilhører() == FradragTilhører.EPS }
        val epsFradragSum = epsFradrag.sumByDouble { it.getTotaltFradrag() }

        val diff = epsFradragSum - beløpsgrense

        if (diff <= 0) {
            return søkersFradrag
        }

        return søkersFradrag.plus(
            epsFradrag[0].let // TODO should be replaced - guaranteed by guards for now.
            {
                FradragFactory.ny(
                    type = Fradragstype.BeregnetFradragEPS,
                    beløp = diff,
                    periode = it.getPeriode(),
                    utenlandskInntekt = null,
                    tilhører = it.getTilhører()
                )
            }
        )
    }

    protected fun List<Fradrag>.harNøyaktigEnForventetInntektFor(fradragTilhører: FradragTilhører) =
        singleOrNull { it.getTilhører() == fradragTilhører && it.getFradragstype() == Fradragstype.ForventetInntekt } != null

    protected fun finnFradragsperiode(fradrag: List<Fradrag>) = Periode(
        fradrag.minByOrNull { it.getPeriode().getFraOgMed() }!!.getPeriode().getFraOgMed(),
        fradrag.maxByOrNull { it.getPeriode().getTilOgMed() }!!.getPeriode().getTilOgMed()
    )
}
