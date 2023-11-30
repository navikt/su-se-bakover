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
import no.nav.su.se.bakover.domain.grunnlag.Utenlandsoppholdgrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje.Companion.lagTidslinje
import vilkår.domain.Inngangsvilkår
import java.util.UUID

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

data class VurderingsperiodeUtenlandsopphold private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val vurdering: Vurdering,
    override val grunnlag: Utenlandsoppholdgrunnlag?,
    override val periode: Periode,
) : Vurderingsperiode, KanPlasseresPåTidslinje<VurderingsperiodeUtenlandsopphold> {

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodeUtenlandsopphold {
        return create(
            id = id,
            opprettet = opprettet,
            vurdering = vurdering,
            periode = stønadsperiode.periode,
            grunnlag = this.grunnlag?.oppdaterPeriode(stønadsperiode.periode),
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodeUtenlandsopphold = when (args) {
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
        return other is VurderingsperiodeUtenlandsopphold &&
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
            grunnlag: Utenlandsoppholdgrunnlag?,
            periode: Periode,
        ): VurderingsperiodeUtenlandsopphold {
            return tryCreate(id, opprettet, vurdering, grunnlag, periode).getOrElse {
                throw IllegalArgumentException(it.toString())
            }
        }

        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            vurdering: Vurdering,
            grunnlag: Utenlandsoppholdgrunnlag?,
            vurderingsperiode: Periode,
        ): Either<UgyldigVurderingsperiode, VurderingsperiodeUtenlandsopphold> {
            grunnlag?.let {
                if (vurderingsperiode != it.periode) return UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig.left()
            }

            return VurderingsperiodeUtenlandsopphold(
                id = id,
                opprettet = opprettet,
                vurdering = vurdering,
                grunnlag = grunnlag,
                periode = vurderingsperiode,
            ).right()
        }
    }

    sealed interface UgyldigVurderingsperiode {
        data object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigVurderingsperiode
    }
}
