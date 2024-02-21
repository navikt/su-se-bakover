package vilkår.opplysningsplikt.domain

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
import vilkår.common.domain.Avslagsgrunn
import vilkår.common.domain.IkkeVurdertVilkår
import vilkår.common.domain.Inngangsvilkår
import vilkår.common.domain.Vilkår
import vilkår.common.domain.Vurdering
import vilkår.common.domain.Vurderingsperiode
import vilkår.common.domain.VurdertVilkår
import vilkår.common.domain.erLik
import vilkår.common.domain.kastHvisPerioderErUsortertEllerHarDuplikater
import vilkår.common.domain.kronologisk
import vilkår.common.domain.slåSammenLikePerioder

sealed interface OpplysningspliktVilkår : Vilkår {
    override val vilkår: Inngangsvilkår.Opplysningsplikt get() = Inngangsvilkår.Opplysningsplikt
    override val grunnlag: List<Opplysningspliktgrunnlag>

    abstract override fun lagTidslinje(periode: Periode): OpplysningspliktVilkår
    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): OpplysningspliktVilkår
    abstract override fun slåSammenLikePerioder(): OpplysningspliktVilkår

    data object IkkeVurdert : OpplysningspliktVilkår, IkkeVurdertVilkår {
        override val grunnlag: List<Opplysningspliktgrunnlag> = emptyList()
        override fun lagTidslinje(periode: Periode): OpplysningspliktVilkår = this
        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): OpplysningspliktVilkår = this
        override fun erLik(other: Vilkår): Boolean = other is IkkeVurdert
    }

    data class Vurdert private constructor(
        override val vurderingsperioder: Nel<VurderingsperiodeOpplysningsplikt>,
    ) : OpplysningspliktVilkår, VurdertVilkår {

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
            require(!vurderingsperioder.harOverlappende())
        }

        override val grunnlag: List<Opplysningspliktgrunnlag> = vurderingsperioder.map { it.grunnlag }

        override fun lagTidslinje(periode: Periode): OpplysningspliktVilkår {
            return copy(
                vurderingsperioder = vurderingsperioder
                    .lagTidslinje()
                    .krympTilPeriode(periode)!!
                    .toNonEmptyList(),
            )
        }

        override val avslagsgrunner: List<Avslagsgrunn> = when (vurdering) {
            Vurdering.Innvilget -> emptyList()
            Vurdering.Uavklart -> emptyList()
            Vurdering.Avslag -> listOf(Avslagsgrunn.MANGLENDE_DOKUMENTASJON)
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): OpplysningspliktVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn én vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }

        override fun slåSammenLikePerioder(): OpplysningspliktVilkår {
            return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }

        override fun copyWithNewId(): Vurdert {
            return this.copy(vurderingsperioder = vurderingsperioder.map { it.copyWithNewId() })
        }

        companion object {
            fun createFromVilkårsvurderinger(
                vurderingsperioder: Nel<VurderingsperiodeOpplysningsplikt>,
            ): Vurdert {
                return tryCreate(vurderingsperioder).getOrElse { throw IllegalArgumentException(it.toString()) }
            }

            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodeOpplysningsplikt>,
            ): Either<KunneIkkeLageOpplysningspliktVilkår, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return KunneIkkeLageOpplysningspliktVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder.kronologisk()).right()
            }
        }
    }
}

sealed interface KunneIkkeLageOpplysningspliktVilkår {
    sealed interface Vurderingsperiode : KunneIkkeLageOpplysningspliktVilkår {
        data object PeriodeForGrunnlagOgVurderingErForskjellig : Vurderingsperiode
    }

    data object OverlappendeVurderingsperioder : KunneIkkeLageOpplysningspliktVilkår
}
