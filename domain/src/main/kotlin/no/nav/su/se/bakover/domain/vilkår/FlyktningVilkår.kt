package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import vilkår.domain.Inngangsvilkår
import vilkår.domain.grunnlag.Grunnlag
import java.util.UUID

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

data class VurderingsperiodeFlyktning private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val vurdering: Vurdering,
    override val periode: Periode,
) : Vurderingsperiode, KanPlasseresPåTidslinje<VurderingsperiodeFlyktning> {
    override val grunnlag: Grunnlag? = null

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodeFlyktning {
        return create(
            id = id,
            opprettet = opprettet,
            vurdering = vurdering,
            periode = stønadsperiode.periode,
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodeFlyktning = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(
                id = UUID.randomUUID(),
            )
        }

        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(
                id = UUID.randomUUID(),
                periode = args.periode,
            )
        }
    }

    override fun erLik(other: Vurderingsperiode): Boolean {
        return other is VurderingsperiodeFlyktning && vurdering == other.vurdering
    }

    companion object {
        fun create(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            vurdering: Vurdering,
            periode: Periode,
        ): VurderingsperiodeFlyktning {
            return VurderingsperiodeFlyktning(
                id = id,
                opprettet = opprettet,
                vurdering = vurdering,
                periode = periode,
            )
        }
    }
}
