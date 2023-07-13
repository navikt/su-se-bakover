package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import no.nav.su.se.bakover.domain.grunnlag.LovligOppholdGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.lagTidslinje

sealed interface LovligOppholdVilkår : Vilkår {
    override val vilkår get() = Inngangsvilkår.LovligOpphold
    val grunnlag: List<LovligOppholdGrunnlag>

    abstract override fun lagTidslinje(periode: Periode): LovligOppholdVilkår
    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): LovligOppholdVilkår
    abstract override fun slåSammenLikePerioder(): LovligOppholdVilkår

    data object IkkeVurdert : LovligOppholdVilkår, IkkeVurdertVilkår {
        override val grunnlag = emptyList<LovligOppholdGrunnlag>()
        override fun lagTidslinje(periode: Periode): LovligOppholdVilkår = this
        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): LovligOppholdVilkår = this
        override fun erLik(other: Vilkår): Boolean = other is IkkeVurdert
    }

    data class Vurdert private constructor(
        override val vurderingsperioder: Nel<VurderingsperiodeLovligOpphold>,
    ) : LovligOppholdVilkår, VurdertVilkår {

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
            require(!vurderingsperioder.harOverlappende())
        }

        override val grunnlag: List<LovligOppholdGrunnlag> = vurderingsperioder.mapNotNull { it.grunnlag }

        override fun lagTidslinje(periode: Periode): LovligOppholdVilkår {
            return copy(
                vurderingsperioder = vurderingsperioder.lagTidslinje().krympTilPeriode(periode)!!.toNonEmptyList(),
            )
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): LovligOppholdVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn én vurdering" }
            return copy(
                vurderingsperioder = nonEmptyListOf(
                    vurderingsperioder.first().oppdaterStønadsperiode(stønadsperiode),
                ),
            )
        }

        override fun slåSammenLikePerioder(): LovligOppholdVilkår {
            return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }

        companion object {
            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodeLovligOpphold>,
            ): Either<KunneIkkeLageLovligOppholdVilkår.OverlappendeVurderingsperioder, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return KunneIkkeLageLovligOppholdVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder.kronologisk()).right()
            }

            fun createFromVilkårsvurderinger(
                vurderingsperioder: Nel<VurderingsperiodeLovligOpphold>,
            ) = tryCreate(vurderingsperioder).getOrElse { throw IllegalArgumentException(it.toString()) }
        }
    }
}

sealed interface KunneIkkeLageLovligOppholdVilkår {
    sealed interface Vurderingsperiode : KunneIkkeLageLovligOppholdVilkår {
        data object PeriodeForGrunnlagOgVurderingErForskjellig : Vurderingsperiode
    }

    data object OverlappendeVurderingsperioder : KunneIkkeLageLovligOppholdVilkår
}
