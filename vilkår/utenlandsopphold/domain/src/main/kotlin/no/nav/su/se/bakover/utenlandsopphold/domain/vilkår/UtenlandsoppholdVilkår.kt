package no.nav.su.se.bakover.utenlandsopphold.domain.vilkår

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
import vilkår.domain.IkkeVurdertVilkår
import vilkår.domain.Inngangsvilkår
import vilkår.domain.Vilkår
import vilkår.domain.VurdertVilkår
import vilkår.domain.erLik
import vilkår.domain.kastHvisPerioderErUsortertEllerHarDuplikater
import vilkår.domain.kronologisk
import vilkår.domain.slåSammenLikePerioder

sealed interface UtenlandsoppholdVilkår : Vilkår {
    override val vilkår get() = Inngangsvilkår.Utenlandsopphold
    val grunnlag: List<Utenlandsoppholdgrunnlag>

    abstract override fun lagTidslinje(periode: Periode): UtenlandsoppholdVilkår
    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): UtenlandsoppholdVilkår
    abstract override fun slåSammenLikePerioder(): UtenlandsoppholdVilkår

    data object IkkeVurdert : UtenlandsoppholdVilkår, IkkeVurdertVilkår {
        override val grunnlag = emptyList<Utenlandsoppholdgrunnlag>()
        override fun lagTidslinje(periode: Periode): UtenlandsoppholdVilkår = this
        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): UtenlandsoppholdVilkår = this
        override fun erLik(other: Vilkår): Boolean = other is IkkeVurdert
    }

    data class Vurdert private constructor(
        override val vurderingsperioder: Nel<VurderingsperiodeUtenlandsopphold>,
    ) : UtenlandsoppholdVilkår, VurdertVilkår {

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
            require(!vurderingsperioder.harOverlappende())
        }

        override val grunnlag: List<Utenlandsoppholdgrunnlag> = vurderingsperioder.mapNotNull { it.grunnlag }

        override fun lagTidslinje(periode: Periode): UtenlandsoppholdVilkår {
            return copy(
                vurderingsperioder = vurderingsperioder.lagTidslinje().krympTilPeriode(periode)!!.toNonEmptyList(),
            )
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): UtenlandsoppholdVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn én vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }

        override fun slåSammenLikePerioder(): UtenlandsoppholdVilkår {
            return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }

        companion object {
            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodeUtenlandsopphold>,
            ): Either<UgyldigUtenlandsoppholdVilkår, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return UgyldigUtenlandsoppholdVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder.kronologisk()).right()
            }

            fun createFromVilkårsvurderinger(
                vurderingsperioder: Nel<VurderingsperiodeUtenlandsopphold>,
            ): Vurdert =
                tryCreateFromVurderingsperioder(vurderingsperioder).getOrElse { throw IllegalArgumentException(it.toString()) }

            fun tryCreateFromVurderingsperioder(
                vurderingsperioder: Nel<VurderingsperiodeUtenlandsopphold>,
            ): Either<UgyldigUtenlandsoppholdVilkår, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return UgyldigUtenlandsoppholdVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder.kronologisk()).right()
            }
        }

        sealed interface UgyldigUtenlandsoppholdVilkår {
            data object OverlappendeVurderingsperioder : UgyldigUtenlandsoppholdVilkår
        }
    }
}
