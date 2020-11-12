package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.common.periode.Periode

internal sealed class FradragStrategy {
    abstract fun beregnFradrag(forventetInntekt: Int, fradrag: List<Fradrag>, periode: Periode): List<Fradrag>

    object Enslig : FradragStrategy() {
        override fun beregnFradrag(forventetInntekt: Int, fradrag: List<Fradrag>, periode: Periode): List<Fradrag> =
            bestemFradrag(forventetInntekt, fradrag.filter { it.getTilhører() == FradragTilhører.BRUKER }, periode)
    }

    object EpsOver67År : FradragStrategy() {
        override fun beregnFradrag(forventetInntekt: Int, fradrag: List<Fradrag>, periode: Periode): List<Fradrag> =
            bestemFradrag(forventetInntekt, fradrag, periode)
    }

    object EpsUnder67ÅrOgUførFlyktning : FradragStrategy() {
        override fun beregnFradrag(forventetInntekt: Int, fradrag: List<Fradrag>, periode: Periode): List<Fradrag> =
            bestemFradrag(forventetInntekt, fradrag, periode)
    }

    object EpsUnder67År : FradragStrategy() {
        override fun beregnFradrag(forventetInntekt: Int, fradrag: List<Fradrag>, periode: Periode): List<Fradrag> =
            bestemFradrag(forventetInntekt, fradrag, periode)
    }

    protected fun bestemFradrag(forventetInntekt: Int, fradrag: List<Fradrag>, periode: Periode): List<Fradrag> {
        val (arbeid, andre) = fradrag.partition { it.getFradragstype() == Fradragstype.Arbeidsinntekt }
        val arbeidsinntekt = arbeid.sumByDouble { it.getTotaltFradrag() }
        return if (arbeidsinntekt >= forventetInntekt) fradrag else andre.plus(
            FradragFactory.ny(
                type = Fradragstype.ForventetInntekt,
                beløp = forventetInntekt.toDouble(),
                periode = periode,
                utenlandskInntekt = null,
                tilhører = FradragTilhører.BRUKER
            )
        )
    }
}
