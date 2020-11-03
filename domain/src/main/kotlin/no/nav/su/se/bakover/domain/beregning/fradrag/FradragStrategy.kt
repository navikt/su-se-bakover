package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Periode

sealed class FradragStrategy {
    abstract fun beregnFradrag(forventetInntekt: Int, fradrag: List<IFradrag>, periode: Periode): List<IFradrag>

    object Enslig : FradragStrategy() {
        override fun beregnFradrag(forventetInntekt: Int, fradrag: List<IFradrag>, periode: Periode): List<IFradrag> =
            bestemFradrag(forventetInntekt, fradrag, periode)
    }

    object EpsOver67År : FradragStrategy() {
        override fun beregnFradrag(forventetInntekt: Int, fradrag: List<IFradrag>, periode: Periode): List<IFradrag> =
            bestemFradrag(forventetInntekt, fradrag, periode)
    }

    object EpsUnder67ÅrOgUførFlyktning : FradragStrategy() {
        override fun beregnFradrag(forventetInntekt: Int, fradrag: List<IFradrag>, periode: Periode): List<IFradrag> =
            bestemFradrag(forventetInntekt, fradrag, periode)
    }

    object EpsUnder67År : FradragStrategy() {
        override fun beregnFradrag(forventetInntekt: Int, fradrag: List<IFradrag>, periode: Periode): List<IFradrag> =
            bestemFradrag(forventetInntekt, fradrag, periode)
    }

    protected fun bestemFradrag(forventetInntekt: Int, fradrag: List<IFradrag>, periode: Periode): List<IFradrag> {
        val (arbeid, andre) = fradrag.partition { it.type() == Fradragstype.Arbeidsinntekt }
        val arbeidsinntekt = arbeid.sumByDouble { it.totalBeløp() }
        return if (arbeidsinntekt >= forventetInntekt) fradrag else andre.plus(
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                beløp = forventetInntekt.toDouble(),
                utenlandskInntekt = null,
                periode = periode
            )
        )
    }
}
