package no.nav.su.se.bakover.domain.beregning

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.harOverlappende
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragFactory
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import org.jetbrains.annotations.TestOnly

data class Beregningsgrunnlag private constructor(
    val beregningsperiode: Periode,
    val fradrag: List<Fradrag>,
) {
    companion object {

        @TestOnly
        fun create(
            beregningsperiode: Periode,
            uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
            fradragFraSaksbehandler: List<Grunnlag.Fradragsgrunnlag>,
        ): Beregningsgrunnlag {
            return tryCreate(
                beregningsperiode,
                uføregrunnlag,
                fradragFraSaksbehandler,
            ).getOrElse { throw IllegalArgumentException(it.toString()) }
        }

        fun tryCreate(
            beregningsperiode: Periode,
            uføregrunnlag: List<Grunnlag.Uføregrunnlag>,
            fradragFraSaksbehandler: List<Grunnlag.Fradragsgrunnlag>,
        ): Either<UgyldigBeregningsgrunnlag, Beregningsgrunnlag> {
            val fradrag: List<Fradrag> = fradragFraSaksbehandler.map { it.fradrag }.plus(
                uføregrunnlag.map {
                    FradragFactory.nyFradragsperiode(
                        fradragstype = Fradragstype.ForventetInntekt,
                        månedsbeløp = it.forventetInntekt / 12.0,
                        periode = it.periode,
                        utenlandskInntekt = null,
                        tilhører = FradragTilhører.BRUKER,
                    )
                }.ifEmpty {
                    // TODO jah: Dette er egentlig bare en snarvei for testene som går ut over typesikkerheten.
                    //  Vurder å bytt fra List<Grunnlag.Uføregrunnlag> til NonEmptyList<Grunnlag.Uføregrunnlag>
                    listOf(
                        FradragFactory.nyFradragsperiode(
                            fradragstype = Fradragstype.ForventetInntekt,
                            månedsbeløp = 0.0,
                            periode = beregningsperiode,
                            utenlandskInntekt = null,
                            tilhører = FradragTilhører.BRUKER,
                        ),
                    )
                },
            )
            if (!fradrag.all { beregningsperiode inneholder it.periode }) {
                return UgyldigBeregningsgrunnlag.IkkeLovMedFradragUtenforPerioden.left()
            }

            fradrag.filter { it.fradragstype == Fradragstype.ForventetInntekt && it.tilhører == FradragTilhører.BRUKER }.let { forventedeInntekter ->
                if (forventedeInntekter.isEmpty()) {
                    // TODO jah: Denne kan ikke slå til så lenge vi har ifEmpty-blokka
                    return UgyldigBeregningsgrunnlag.BrukerMåHaMinst1ForventetInntekt.left()
                }
                if (forventedeInntekter.map { it.periode }.harOverlappende()) {
                    return UgyldigBeregningsgrunnlag.OverlappendePerioderMedForventetInntekt.left()
                }
                if (!beregningsperiode.måneder().all { it ->
                        forventedeInntekter.flatMap { it.periode.måneder() }.contains(it)
                    }
                ) {
                    return UgyldigBeregningsgrunnlag.ManglerForventetInntektForEnkelteMåneder.left()
                }
            }

            return Beregningsgrunnlag(beregningsperiode, fradrag).right()
        }
    }
}

sealed class UgyldigBeregningsgrunnlag {
    object IkkeLovMedFradragUtenforPerioden : UgyldigBeregningsgrunnlag()
    object BrukerMåHaMinst1ForventetInntekt : UgyldigBeregningsgrunnlag()
    object OverlappendePerioderMedForventetInntekt : UgyldigBeregningsgrunnlag()
    object ManglerForventetInntektForEnkelteMåneder : UgyldigBeregningsgrunnlag()
}
