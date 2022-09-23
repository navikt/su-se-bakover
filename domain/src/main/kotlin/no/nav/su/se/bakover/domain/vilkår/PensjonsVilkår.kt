package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.harOverlappende
import no.nav.su.se.bakover.common.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.grunnlag.Pensjonsgrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import java.time.LocalDate
import java.util.UUID

sealed class PensjonsVilkår : Vilkår() {
    override val vilkår = Inngangsvilkår.Pensjon
    abstract val grunnlag: List<Pensjonsgrunnlag>

    abstract override fun lagTidslinje(periode: Periode): PensjonsVilkår
    abstract fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): PensjonsVilkår
    abstract override fun slåSammenLikePerioder(): PensjonsVilkår

    object IkkeVurdert : PensjonsVilkår() {
        override val vurdering: Vurdering = Vurdering.Uavklart
        override val erAvslag = false
        override val erInnvilget = false
        override val grunnlag = emptyList<Pensjonsgrunnlag>()
        override val perioder: List<Periode> = emptyList()

        override fun lagTidslinje(periode: Periode): PensjonsVilkår {
            return this
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): PensjonsVilkår {
            return this
        }

        override fun hentTidligesteDatoForAvslag(): LocalDate? = null
        override fun erLik(other: Vilkår): Boolean {
            return other is IkkeVurdert
        }
    }

    data class Vurdert private constructor(
        val vurderingsperioder: Nel<VurderingsperiodePensjon>,
    ) : PensjonsVilkår() {

        override val grunnlag: List<Pensjonsgrunnlag> = vurderingsperioder.mapNotNull { it.grunnlag }
        override fun lagTidslinje(periode: Periode): PensjonsVilkår {
            return copy(
                vurderingsperioder = Tidslinje(
                    periode = periode,
                    objekter = vurderingsperioder,
                ).tidslinje.toNonEmptyList(),
            )
        }

        override val erInnvilget: Boolean = vurderingsperioder.all { it.vurdering == Vurdering.Innvilget }
        override val erAvslag: Boolean = vurderingsperioder.any { it.vurdering == Vurdering.Avslag }

        override val vurdering: Vurdering =
            if (erInnvilget) Vurdering.Innvilget else if (erAvslag) Vurdering.Avslag else Vurdering.Uavklart

        override val perioder: Nel<Periode> = vurderingsperioder.minsteAntallSammenhengendePerioder()

        override fun hentTidligesteDatoForAvslag(): LocalDate? {
            return vurderingsperioder
                .filter { it.vurdering == Vurdering.Avslag }
                .map { it.periode.fraOgMed }
                .minByOrNull { it }
        }

        override fun erLik(other: Vilkår): Boolean {
            return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
        }

        fun minsteAntallSammenhengendePerioder(): List<Periode> {
            return vurderingsperioder.map { it.periode }.minsteAntallSammenhengendePerioder()
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
                tryCreateFromVurderingsperioder(vurderingsperioder).getOrHandle { throw IllegalArgumentException(it.toString()) }

            fun tryCreateFromVurderingsperioder(
                vurderingsperioder: Nel<VurderingsperiodePensjon>,
            ): Either<KunneIkkeLagePensjonsVilkår, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return KunneIkkeLagePensjonsVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder.kronologisk()).right()
            }
        }

        sealed class UgyldigPensjonsVilkår {
            object OverlappendeVurderingsperioder : UgyldigPensjonsVilkår()
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

        init {
            kastHvisPerioderErUsortertEllerHarDuplikater()
        }
    }
}

data class VurderingsperiodePensjon private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val vurdering: Vurdering,
    override val grunnlag: Pensjonsgrunnlag,
    override val periode: Periode,
) : Vurderingsperiode(), KanPlasseresPåTidslinje<VurderingsperiodePensjon> {

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
        is CopyArgs.Tidslinje.Maskert -> {
            copy(args.args).copy(opprettet = opprettet.plusUnits(1))
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
            return tryCreate(id, opprettet, periode, grunnlag).getOrHandle {
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
        object PeriodeForGrunnlagOgVurderingErForskjellig : Vurderingsperiode
    }

    object OverlappendeVurderingsperioder : KunneIkkeLagePensjonsVilkår
}
