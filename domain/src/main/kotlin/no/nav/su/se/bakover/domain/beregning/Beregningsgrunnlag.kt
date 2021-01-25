package no.nav.su.se.bakover.domain.beregning

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype

data class Beregningsgrunnlag private constructor (
    val beregningsperiode: Periode,
    val fradrag: List<Fradrag>
) {
    companion object {
        fun create(beregningsperiode: Periode, forventetInntektPerÅr: Double, fradragFraSaksbehandler: List<Fradrag>): Beregningsgrunnlag {
            return tryCreate(beregningsperiode, forventetInntektPerÅr, fradragFraSaksbehandler).getOrHandle { throw IllegalArgumentException(it.toString()) }
        }

        private fun tryCreate(beregningsperiode: Periode, forventetInntektPerÅr: Double, fradragFraSaksbehandler: List<Fradrag>): Either<UgyldigBeregningsgrunnlag, Beregningsgrunnlag> {
            val fradrag: List<Fradrag> = fradragFraSaksbehandler.plus(
                FradragFactory.ny(
                    type = Fradragstype.ForventetInntekt,
                    månedsbeløp = forventetInntektPerÅr / 12.0,
                    periode = beregningsperiode,
                    utenlandskInntekt = null,
                    tilhører = FradragTilhører.BRUKER
                )
            )
            if (!fradrag.all { beregningsperiode inneholder it.getPeriode() }) { return UgyldigBeregningsgrunnlag.IkkeLovMedFradragUtenforPerioden.left() }
            if (fradrag.count { it.getFradragstype() == Fradragstype.ForventetInntekt && it.getTilhører() == FradragTilhører.BRUKER } != 1) { return UgyldigBeregningsgrunnlag.BrukerMåHaNøyaktig1ForventetInntekt.left() }

            return Beregningsgrunnlag(beregningsperiode, fradrag).right()
        }
    }

    sealed class UgyldigBeregningsgrunnlag {
        object IkkeLovMedFradragUtenforPerioden : UgyldigBeregningsgrunnlag()
        object BrukerMåHaNøyaktig1ForventetInntekt : UgyldigBeregningsgrunnlag()
    }
}
