package vilkår.pensjon.domain

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import vilkår.common.domain.IkkeVurdertVilkår
import vilkår.common.domain.Inngangsvilkår
import vilkår.common.domain.Vilkår
import vilkår.common.domain.Vurderingsperiode
import vilkår.common.domain.VurdertVilkår
import vilkår.common.domain.erLik
import vilkår.common.domain.kastHvisPerioderErUsortertEllerHarDuplikater
import vilkår.common.domain.kronologisk
import vilkår.common.domain.slåSammenLikePerioder

sealed interface PensjonsVilkår : Vilkår {
    override val vilkår get() = Inngangsvilkår.Pensjon
    val grunnlag: List<Pensjonsgrunnlag>

    abstract override fun lagTidslinje(periode: Periode): PensjonsVilkår
    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): PensjonsVilkår
    abstract override fun slåSammenLikePerioder(): PensjonsVilkår

    data object IkkeVurdert : PensjonsVilkår, IkkeVurdertVilkår {
        override val grunnlag = emptyList<Pensjonsgrunnlag>()
        override fun lagTidslinje(periode: Periode): PensjonsVilkår = this
        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): PensjonsVilkår = this
        override fun erLik(other: Vilkår): Boolean = other is IkkeVurdert
    }

    data class Vurdert private constructor(
        override val vurderingsperioder: Nel<VurderingsperiodePensjon>,
    ) : PensjonsVilkår, VurdertVilkår {

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
            require(!vurderingsperioder.harOverlappende())
        }

        override val grunnlag: List<Pensjonsgrunnlag> = vurderingsperioder.map { it.grunnlag }

        override fun lagTidslinje(periode: Periode): PensjonsVilkår {
            return copy(
                vurderingsperioder = vurderingsperioder.lagTidslinje().krympTilPeriode(periode)!!.toNonEmptyList(),
            )
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): PensjonsVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn én vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }

        override fun slåSammenLikePerioder(): PensjonsVilkår {
            return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }

        companion object {
            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodePensjon>,
            ): Either<KunneIkkeLagePensjonsVilkår, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return KunneIkkeLagePensjonsVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder.kronologisk()).right()
            }

            fun createFromVilkårsvurderinger(
                vurderingsperioder: Nel<VurderingsperiodePensjon>,
            ): Vurdert =
                tryCreateFromVurderingsperioder(vurderingsperioder).getOrElse { throw IllegalArgumentException(it.toString()) }

            fun tryCreateFromVurderingsperioder(
                vurderingsperioder: Nel<VurderingsperiodePensjon>,
            ): Either<KunneIkkeLagePensjonsVilkår, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return KunneIkkeLagePensjonsVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder.kronologisk()).right()
            }
        }

        sealed interface UgyldigPensjonsVilkår {
            data object OverlappendeVurderingsperioder : UgyldigPensjonsVilkår
        }
    }
}

sealed interface KunneIkkeLagePensjonsVilkår {
    sealed interface Vurderingsperiode : KunneIkkeLagePensjonsVilkår {
        data object PeriodeForGrunnlagOgVurderingErForskjellig : Vurderingsperiode
    }

    data object OverlappendeVurderingsperioder : KunneIkkeLagePensjonsVilkår
}
