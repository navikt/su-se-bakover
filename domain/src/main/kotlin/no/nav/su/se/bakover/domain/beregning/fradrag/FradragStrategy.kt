package no.nav.su.se.bakover.domain.beregning.fradrag

import arrow.syntax.function.pipe
import no.nav.su.se.bakover.domain.Minstepensjonsnivå
import java.time.LocalDate

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

        val diff = epsFradragSum - Minstepensjonsnivå.Ordinær.forDato(LocalDate.now())

        if (diff <= 0) {
            return søkersFradrag
        }

        return søkersFradrag
            .plus(
                epsFradrag[0].let {
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
}
