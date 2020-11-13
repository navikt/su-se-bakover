package no.nav.su.se.bakover.domain.beregning.fradrag

import arrow.syntax.function.pipe
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.Minstepensjonsnivå

internal sealed class FradragStrategy {
    fun beregn(fradrag: List<Fradrag>): List<Fradrag> {
        validate(fradrag)
        return beregnFradrag(fradrag)
    }

    protected open fun validate(fradrag: List<Fradrag>) {
        require(
            fradrag.singleOrNull {
                it.getTilhører() == FradragTilhører.BRUKER && it.getFradragstype() == Fradragstype.ForventetInntekt
            } != null
        ) { "Fradrag må inneholde brukers forventede inntekt etter uførhet." }
        require(finnFradragsperiode(fradrag).getAntallMåneder() == 12) { "Beregning av fradrag støtter kun behandling av 12-måneders perioder" }
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
            return fradrag
                .pipe { `filtrer ut den laveste av arbeidsinntekt og forventet inntekt`(FradragTilhører.BRUKER, it) }
                .pipe(::`trekk fra ordinær mpn fra EPS sine fradrag`)
        }
    }

    object EpsUnder67ÅrOgUførFlyktning : FradragStrategy() {
        override fun beregnFradrag(fradrag: List<Fradrag>): List<Fradrag> =
            `filtrer ut den laveste av arbeidsinntekt og forventet inntekt`(FradragTilhører.BRUKER, fradrag)
    }

    object EpsUnder67År : FradragStrategy() {
        override fun beregnFradrag(fradrag: List<Fradrag>): List<Fradrag> =
            `filtrer ut den laveste av arbeidsinntekt og forventet inntekt`(FradragTilhører.BRUKER, fradrag)
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

    protected fun `trekk fra ordinær mpn fra EPS sine fradrag`(fradrag: List<Fradrag>): List<Fradrag> {
        val (epsFradrag, søkersFradrag) = fradrag.partition { it.getTilhører() == FradragTilhører.EPS }
        val epsFradragSum = epsFradrag.sumByDouble { it.getTotaltFradrag() }

        val periode = finnFradragsperiode(fradrag).also {
            require(it.getAntallMåneder() == 12) { "Udefinert oppførsel for tilfeller med periode ulik 12 måneder" }
        }

        val diff = epsFradragSum - periodisertSumMinstepensjonsnivå(periode)

        if (diff <= 0) {
            return søkersFradrag
        }

        return søkersFradrag.plus(
            epsFradrag[0].let
            {
                FradragFactory.ny(
                    type = it.getFradragstype(),
                    beløp = diff,
                    periode = it.getPeriode(),
                    utenlandskInntekt = it.getUtenlandskInntekt(),
                    tilhører = it.getTilhører()
                )
            }
        )
    }

    private fun periodisertSumMinstepensjonsnivå(periode: Periode) =
        Minstepensjonsnivå.Ordinær.periodiser(periode).values.sumByDouble { it }

    private fun finnFradragsperiode(fradrag: List<Fradrag>) = Periode(
        fradrag.minByOrNull { it.getPeriode().getFraOgMed() }!!.getPeriode().getFraOgMed(),
        fradrag.maxByOrNull { it.getPeriode().getTilOgMed() }!!.getPeriode().getTilOgMed()
    )
}
