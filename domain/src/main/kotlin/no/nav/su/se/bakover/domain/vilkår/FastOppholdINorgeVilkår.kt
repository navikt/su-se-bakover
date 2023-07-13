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
import no.nav.su.se.bakover.domain.grunnlag.FastOppholdINorgeGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import java.util.UUID

sealed interface FastOppholdINorgeVilkår : Vilkår {
    override val vilkår get() = Inngangsvilkår.FastOppholdINorge
    val grunnlag: List<FastOppholdINorgeGrunnlag>

    abstract override fun lagTidslinje(periode: Periode): FastOppholdINorgeVilkår
    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): FastOppholdINorgeVilkår
    abstract override fun slåSammenLikePerioder(): FastOppholdINorgeVilkår

    data object IkkeVurdert : FastOppholdINorgeVilkår, IkkeVurdertVilkår {
        override val grunnlag = emptyList<FastOppholdINorgeGrunnlag>()
        override fun lagTidslinje(periode: Periode): FastOppholdINorgeVilkår = this
        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): FastOppholdINorgeVilkår = this
        override fun erLik(other: Vilkår): Boolean = other is IkkeVurdert
    }

    data class Vurdert private constructor(
        override val vurderingsperioder: Nel<VurderingsperiodeFastOppholdINorge>,
    ) : FastOppholdINorgeVilkår, VurdertVilkår {

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
            require(!vurderingsperioder.harOverlappende())
        }

        override val grunnlag: List<FastOppholdINorgeGrunnlag> = vurderingsperioder.mapNotNull { it.grunnlag }

        override fun lagTidslinje(periode: Periode): FastOppholdINorgeVilkår {
            return copy(
                vurderingsperioder = vurderingsperioder.lagTidslinje().krympTilPeriode(periode)!!.toNonEmptyList(),
            )
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): FastOppholdINorgeVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn én vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }

        override fun slåSammenLikePerioder(): FastOppholdINorgeVilkår {
            return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }

        companion object {
            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodeFastOppholdINorge>,
            ): Either<UgyldigFastOppholdINorgeVikår, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return UgyldigFastOppholdINorgeVikår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder.kronologisk()).right()
            }
        }

        sealed interface UgyldigFastOppholdINorgeVikår {
            data object OverlappendeVurderingsperioder : UgyldigFastOppholdINorgeVikår
        }
    }
}

data class VurderingsperiodeFastOppholdINorge private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val grunnlag: FastOppholdINorgeGrunnlag?,
    override val vurdering: Vurdering,
    override val periode: Periode,
) : Vurderingsperiode, KanPlasseresPåTidslinje<VurderingsperiodeFastOppholdINorge> {

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodeFastOppholdINorge {
        return create(
            id = id,
            opprettet = opprettet,
            vurdering = vurdering,
            periode = stønadsperiode.periode,
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodeFastOppholdINorge = when (args) {
        CopyArgs.Tidslinje.Full -> {
            copy(
                id = UUID.randomUUID(),
                grunnlag = grunnlag?.copy(args),
            )
        }

        is CopyArgs.Tidslinje.NyPeriode -> {
            copy(
                id = UUID.randomUUID(),
                periode = args.periode,
                grunnlag = grunnlag?.copy(args),
            )
        }
    }

    override fun erLik(other: Vurderingsperiode): Boolean {
        return other is VurderingsperiodeFastOppholdINorge &&
            vurdering == other.vurdering &&
            when {
                grunnlag != null && other.grunnlag != null -> grunnlag.erLik(other.grunnlag)
                grunnlag == null && other.grunnlag == null -> true
                else -> false
            }
    }

    companion object {
        fun create(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            vurdering: Vurdering,
            periode: Periode,
        ): VurderingsperiodeFastOppholdINorge {
            return tryCreate(id, opprettet, vurdering, periode).getOrElse {
                throw IllegalArgumentException(it.toString())
            }
        }

        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            vurdering: Vurdering,
            vurderingsperiode: Periode,
        ): Either<UgyldigVurderingsperiode, VurderingsperiodeFastOppholdINorge> {
            return VurderingsperiodeFastOppholdINorge(
                id = id,
                opprettet = opprettet,
                vurdering = vurdering,
                periode = vurderingsperiode,
                grunnlag = null,
            ).right()
        }
    }

    sealed interface UgyldigVurderingsperiode {
        data object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigVurderingsperiode
    }
}
