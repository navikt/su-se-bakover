package vilkår.familiegjenforening.domain

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
import vilkår.common.domain.VurdertVilkår
import vilkår.common.domain.erLik
import vilkår.common.domain.kastHvisPerioderErUsortertEllerHarDuplikater
import vilkår.common.domain.slåSammenLikePerioder

sealed interface FamiliegjenforeningVilkår : Vilkår {
    override val vilkår: Inngangsvilkår get() = Inngangsvilkår.Familiegjenforening

    abstract override fun lagTidslinje(periode: Periode): FamiliegjenforeningVilkår
    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): FamiliegjenforeningVilkår

    data object IkkeVurdert : FamiliegjenforeningVilkår, IkkeVurdertVilkår {
        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun erLik(other: Vilkår): Boolean = other is IkkeVurdert
        override fun lagTidslinje(periode: Periode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): IkkeVurdert = this
    }

    data class Vurdert private constructor(
        override val vurderingsperioder: Nel<VurderingsperiodeFamiliegjenforening>,
    ) : FamiliegjenforeningVilkår, VurdertVilkår {

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
            require(!vurderingsperioder.harOverlappende())
        }

        override val avslagsgrunner: List<Avslagsgrunn> = when (vurdering) {
            Vurdering.Innvilget -> emptyList()
            Vurdering.Uavklart -> emptyList()
            Vurdering.Avslag -> listOf(Avslagsgrunn.FAMILIEGJENFORENING)
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        override fun slåSammenLikePerioder(): Vurdert {
            return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }

        override fun copyWithNewId(): FamiliegjenforeningVilkår =
            this.copy(vurderingsperioder = vurderingsperioder.map { it.copyWithNewId() })

        override fun lagTidslinje(periode: Periode): Vurdert {
            return copy(
                vurderingsperioder = vurderingsperioder.lagTidslinje().krympTilPeriode(periode)!!.toNonEmptyList(),
            )
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): FamiliegjenforeningVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med mer enn én vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map { it.oppdaterStønadsperiode(stønadsperiode) },
            )
        }

        companion object {
            fun create(
                vurderingsperioder: Nel<VurderingsperiodeFamiliegjenforening>,
            ) =
                if (vurderingsperioder.harOverlappende()) {
                    UgyldigFamiliegjenforeningVilkår.OverlappendeVurderingsperioder.left()
                } else {
                    Vurdert(vurderingsperioder).right()
                }

            fun createFromVilkårsvurderinger(
                vurderingsperioder: Nel<VurderingsperiodeFamiliegjenforening>,
            ) = create(vurderingsperioder).getOrElse { throw IllegalArgumentException(it.toString()) }
        }
    }
}

sealed interface UgyldigFamiliegjenforeningVilkår {
    data object OverlappendeVurderingsperioder : UgyldigFamiliegjenforeningVilkår
}
