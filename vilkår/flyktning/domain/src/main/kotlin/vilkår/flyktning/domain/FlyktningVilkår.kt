package vilkår.flyktning.domain

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

sealed interface FlyktningVilkår : Vilkår {
    override val vilkår get() = Inngangsvilkår.Flyktning

    abstract override fun lagTidslinje(periode: Periode): FlyktningVilkår
    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): FlyktningVilkår
    abstract override fun slåSammenLikePerioder(): FlyktningVilkår

    data object IkkeVurdert : FlyktningVilkår, IkkeVurdertVilkår {
        override fun lagTidslinje(periode: Periode): FlyktningVilkår = this
        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): FlyktningVilkår = this
        override fun erLik(other: Vilkår): Boolean = other is IkkeVurdert
    }

    data class Vurdert private constructor(
        override val vurderingsperioder: Nel<VurderingsperiodeFlyktning>,
    ) : FlyktningVilkår, VurdertVilkår {

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
        }

        override fun lagTidslinje(periode: Periode): FlyktningVilkår =
            copy(vurderingsperioder = vurderingsperioder.lagTidslinje().krympTilPeriode(periode)!!.toNonEmptyList())

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): FlyktningVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn én vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }

        override fun slåSammenLikePerioder(): FlyktningVilkår {
            return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }

        companion object {

            fun create(vurderingsperioder: Nel<VurderingsperiodeFlyktning>): Vurdert {
                return tryCreate(vurderingsperioder).getOrElse { throw IllegalArgumentException(it.toString()) }
            }

            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodeFlyktning>,
            ): Either<UgyldigFlyktningVilkår, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return UgyldigFlyktningVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder.kronologisk()).right()
            }
        }

        sealed interface UgyldigFlyktningVilkår {
            data object OverlappendeVurderingsperioder : UgyldigFlyktningVilkår
        }
    }
}
