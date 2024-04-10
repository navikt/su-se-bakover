package vilkår.personligoppmøte.domain

import arrow.core.Nel
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.domain.tidslinje.Tidslinje.Companion.lagTidslinje
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

sealed interface PersonligOppmøteVilkår : Vilkår {
    override val vilkår get() = Inngangsvilkår.PersonligOppmøte
    override val grunnlag: List<PersonligOppmøteGrunnlag>

    abstract override fun lagTidslinje(periode: Periode): PersonligOppmøteVilkår
    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): PersonligOppmøteVilkår
    abstract override fun slåSammenLikePerioder(): PersonligOppmøteVilkår

    data object IkkeVurdert : PersonligOppmøteVilkår, IkkeVurdertVilkår {
        override val grunnlag = emptyList<PersonligOppmøteGrunnlag>()
        override fun lagTidslinje(periode: Periode): PersonligOppmøteVilkår = this
        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): PersonligOppmøteVilkår = this
        override fun erLik(other: Vilkår): Boolean = other is IkkeVurdert
    }

    data class Vurdert(
        override val vurderingsperioder: Nel<VurderingsperiodePersonligOppmøte>,
    ) : PersonligOppmøteVilkår, VurdertVilkår {

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
            require(!vurderingsperioder.harOverlappende())
        }

        override val grunnlag: List<PersonligOppmøteGrunnlag> = vurderingsperioder.map { it.grunnlag }

        override fun lagTidslinje(periode: Periode): PersonligOppmøteVilkår {
            return copy(
                vurderingsperioder = vurderingsperioder.lagTidslinje().krympTilPeriode(periode)!!.toNonEmptyList(),
            )
        }

        override val avslagsgrunner: List<Avslagsgrunn> = when (vurdering) {
            Vurdering.Innvilget -> emptyList()
            Vurdering.Uavklart -> emptyList()
            Vurdering.Avslag -> listOf(Avslagsgrunn.PERSONLIG_OPPMØTE)
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): PersonligOppmøteVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn én vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }

        override fun slåSammenLikePerioder(): PersonligOppmøteVilkår {
            return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }

        override fun copyWithNewId(): Vurdert =
            this.copy(vurderingsperioder = vurderingsperioder.map { it.copyWithNewId() })
    }
}
