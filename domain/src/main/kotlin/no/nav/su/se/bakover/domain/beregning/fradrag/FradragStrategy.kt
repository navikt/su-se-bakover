package no.nav.su.se.bakover.domain.beregning.fradrag

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
            return bestemFradrag(fradrag.filter { it.getTilhører() == FradragTilhører.BRUKER })
        }
    }

    object EpsOver67År : FradragStrategy() {
        override fun beregnFradrag(fradrag: List<Fradrag>): List<Fradrag> =
            bestemFradrag(fradrag)
    }

    object EpsUnder67ÅrOgUførFlyktning : FradragStrategy() {
        override fun beregnFradrag(fradrag: List<Fradrag>): List<Fradrag> =
            bestemFradrag(fradrag)
    }

    object EpsUnder67År : FradragStrategy() {
        override fun beregnFradrag(fradrag: List<Fradrag>): List<Fradrag> =
            bestemFradrag(fradrag)
    }

    protected fun bestemFradrag(fradrag: List<Fradrag>): List<Fradrag> {
        val arbeidsinntekter = fradrag.filter { it.getFradragstype() == Fradragstype.Arbeidsinntekt }
        val forventetInntekt = fradrag.filter { it.getFradragstype() == Fradragstype.ForventetInntekt }
        return if (arbeidsinntekter.sumByDouble { it.getTotaltFradrag() } > forventetInntekt.sumByDouble { it.getTotaltFradrag() })
            fradrag.filter { !forventetInntekt.contains(it) }
        else
            fradrag.filter { !arbeidsinntekter.contains(it) }
    }
}
