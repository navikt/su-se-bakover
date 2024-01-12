package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Nel
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import no.nav.su.se.bakover.domain.grunnlag.PersonligOppmøteGrunnlag
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import vilkår.domain.Inngangsvilkår
import java.util.UUID

sealed interface PersonligOppmøteVilkår : Vilkår {
    override val vilkår get() = Inngangsvilkår.PersonligOppmøte
    val grunnlag: List<PersonligOppmøteGrunnlag>

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
    }
}

data class VurderingsperiodePersonligOppmøte(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val grunnlag: PersonligOppmøteGrunnlag,
    override val periode: Periode,
) : Vurderingsperiode, KanPlasseresPåTidslinje<VurderingsperiodePersonligOppmøte> {
    override val vurdering: Vurdering = grunnlag.vurdering()

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodePersonligOppmøte {
        return VurderingsperiodePersonligOppmøte(
            id = id,
            opprettet = opprettet,
            grunnlag = grunnlag.oppdaterPeriode(stønadsperiode.periode),
            periode = stønadsperiode.periode,
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodePersonligOppmøte = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(
                id = UUID.randomUUID(),
                grunnlag = grunnlag.copy(args),
            )
        }

        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(
                id = UUID.randomUUID(),
                periode = args.periode,
                grunnlag = grunnlag.copy(args),
            )
        }
    }

    override fun erLik(other: Vurderingsperiode): Boolean {
        return other is VurderingsperiodePersonligOppmøte &&
            vurdering == other.vurdering &&
            grunnlag.erLik(other.grunnlag)
    }
}
