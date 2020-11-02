package no.nav.su.se.bakover.domain.beregning.fradrag

import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Fradragstype

sealed class FradragStrategy {
    abstract fun beregnFradrag(forventetInntekt: Int, fradrag: List<Fradrag>): List<Fradrag>

    object Enslig : FradragStrategy() {
        override fun beregnFradrag(forventetInntekt: Int, fradrag: List<Fradrag>): List<Fradrag> =
            bestemFradrag(forventetInntekt, fradrag)
    }

    object EpsOver67År : FradragStrategy() {
        override fun beregnFradrag(forventetInntekt: Int, fradrag: List<Fradrag>): List<Fradrag> =
            bestemFradrag(forventetInntekt, fradrag)
    }

    object EpsUnder67ÅrOgUførFlyktning : FradragStrategy() {
        override fun beregnFradrag(forventetInntekt: Int, fradrag: List<Fradrag>): List<Fradrag> =
            bestemFradrag(forventetInntekt, fradrag)
    }

    object EpsUnder67År : FradragStrategy() {
        override fun beregnFradrag(forventetInntekt: Int, fradrag: List<Fradrag>): List<Fradrag> =
            bestemFradrag(forventetInntekt, fradrag)
    }

    protected fun bestemFradrag(forventetInntekt: Int, fradrag: List<Fradrag>): List<Fradrag> {
        val (arbeid, andre) = fradrag.partition { it.type == Fradragstype.Arbeidsinntekt }
        val arbeidsinntekt = arbeid.sumBy { it.beløp }
        return if (arbeidsinntekt >= forventetInntekt) fradrag else andre.plus(
            Fradrag(
                type = Fradragstype.ForventetInntekt,
                beløp = forventetInntekt,
                utenlandskInntekt = null,
            )
        )
    }
}
