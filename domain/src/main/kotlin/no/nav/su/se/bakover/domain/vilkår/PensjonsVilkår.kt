package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.CopyArgs
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.harOverlappende
import no.nav.su.se.bakover.domain.grunnlag.Pensjonsgrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import java.util.UUID

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

data class VurderingsperiodePensjon private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val vurdering: Vurdering,
    override val grunnlag: Pensjonsgrunnlag,
    override val periode: Periode,
) : Vurderingsperiode, KanPlasseresPåTidslinje<VurderingsperiodePensjon> {

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodePensjon {
        return create(
            id = id,
            opprettet = opprettet,
            periode = stønadsperiode.periode,
            grunnlag = grunnlag,
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodePensjon = when (args) {
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
        return other is VurderingsperiodePensjon &&
            vurdering == other.vurdering &&
            grunnlag.erLik(other.grunnlag)
    }

    companion object {
        fun create(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            periode: Periode,
            grunnlag: Pensjonsgrunnlag,
        ): VurderingsperiodePensjon {
            return tryCreate(id, opprettet, periode, grunnlag).getOrElse {
                throw IllegalArgumentException(it.toString())
            }
        }

        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            vurderingsperiode: Periode,
            grunnlag: Pensjonsgrunnlag,
        ): Either<KunneIkkeLagePensjonsVilkår.Vurderingsperiode, VurderingsperiodePensjon> {
            grunnlag.let {
                if (vurderingsperiode != it.periode) return KunneIkkeLagePensjonsVilkår.Vurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig.left()
            }

            return VurderingsperiodePensjon(
                id = id,
                opprettet = opprettet,
                vurdering = grunnlag.tilResultat(),
                grunnlag = grunnlag,
                periode = vurderingsperiode,
            ).right()
        }
    }
}

sealed interface KunneIkkeLagePensjonsVilkår {
    sealed interface Vurderingsperiode : KunneIkkeLagePensjonsVilkår {
        data object PeriodeForGrunnlagOgVurderingErForskjellig : Vurderingsperiode
    }

    data object OverlappendeVurderingsperioder : KunneIkkeLagePensjonsVilkår
}
