package beregning.domain

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.tid.periode.Måned
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import org.jetbrains.annotations.TestOnly
import vilkår.inntekt.domain.grunnlag.Fradrag
import vilkår.inntekt.domain.grunnlag.FradragFactory
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import vilkår.inntekt.domain.grunnlag.Fradragsgrunnlag
import vilkår.inntekt.domain.grunnlag.Fradragstype
import vilkår.uføre.domain.Uføregrunnlag

data class Beregningsgrunnlag private constructor(
    val beregningsperiode: Periode,
    val fradrag: List<Fradrag>,
) {
    companion object {

        @TestOnly
        fun create(
            beregningsperiode: Periode,
            uføregrunnlag: List<Uføregrunnlag>,
            fradragFraSaksbehandler: List<Fradragsgrunnlag>,
        ): Beregningsgrunnlag {
            return tryCreate(
                beregningsperiode,
                uføregrunnlag,
                fradragFraSaksbehandler,
            ).getOrElse { throw IllegalArgumentException(it.toString()) }
        }

        fun tryCreate(
            beregningsperiode: Periode,
            uføregrunnlag: List<Uføregrunnlag>,
            fradragFraSaksbehandler: List<Fradragsgrunnlag>,
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
                    //  Vurder å bytt fra List<Uføregrunnlag> til NonEmptyList<Uføregrunnlag>
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
                    return UgyldigBeregningsgrunnlag.ManglerForventetInntektForEnkelteMåneder(
                        beregningsperiode.måneder().filterNot { it ->
                            forventedeInntekter.flatMap { it.periode.måneder() }.contains(it)
                        },
                    ).left()
                }
            }

            return Beregningsgrunnlag(beregningsperiode, fradrag).right()
        }
    }
}

sealed interface UgyldigBeregningsgrunnlag {
    data object IkkeLovMedFradragUtenforPerioden : UgyldigBeregningsgrunnlag
    data object BrukerMåHaMinst1ForventetInntekt : UgyldigBeregningsgrunnlag
    data object OverlappendePerioderMedForventetInntekt : UgyldigBeregningsgrunnlag
    data class ManglerForventetInntektForEnkelteMåneder(val måneder: List<Måned>) : UgyldigBeregningsgrunnlag
}
