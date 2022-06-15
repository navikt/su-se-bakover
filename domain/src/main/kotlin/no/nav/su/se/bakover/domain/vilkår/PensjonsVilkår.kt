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
        override val resultat: Resultat = Resultat.Uavklart
        override val erAvslag = false
        override val erInnvilget = false
        override val grunnlag = emptyList<Pensjonsgrunnlag>()
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

        // TODO("init som sjekker at perioder og stuff ikke overlapper - funksjonalitet fra Johns pr på innstrammende inits")

        override val grunnlag: List<Pensjonsgrunnlag> = vurderingsperioder.mapNotNull { it.grunnlag }
        override fun lagTidslinje(periode: Periode): PensjonsVilkår {
            return copy(
                vurderingsperioder = Nel.fromListUnsafe(
                    Tidslinje(
                        periode = periode,
                        objekter = vurderingsperioder,
                    ).tidslinje,
                ),
            )
        }

        override val erInnvilget: Boolean = vurderingsperioder.all { it.resultat == Resultat.Innvilget }

        override val erAvslag: Boolean = vurderingsperioder.any { it.resultat == Resultat.Avslag }

        override val resultat: Resultat =
            if (erInnvilget) Resultat.Innvilget else if (erAvslag) Resultat.Avslag else Resultat.Uavklart

        override fun hentTidligesteDatoForAvslag(): LocalDate? {
            return vurderingsperioder
                .filter { it.resultat == Resultat.Avslag }
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
    }
}

data class VurderingsperiodePensjon private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val resultat: Resultat,
    override val grunnlag: Pensjonsgrunnlag?, // TODO("forsøk å unngå null når vi har funnet ut hva grunnlaget skal inneholde")
    override val periode: Periode,
) : Vurderingsperiode(), KanPlasseresPåTidslinje<VurderingsperiodePensjon> {

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodePensjon {
        return create(
            id = id,
            opprettet = opprettet,
            resultat = resultat,
            periode = stønadsperiode.periode,
            grunnlag = this.grunnlag?.oppdaterPeriode(stønadsperiode.periode),
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodePensjon = when (args) {
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
        is CopyArgs.Tidslinje.Maskert -> {
            copy(args.args).copy(opprettet = opprettet.plusUnits(1))
        }
    }

    override fun erLik(other: Vurderingsperiode): Boolean {
        return other is VurderingsperiodePensjon &&
            resultat == other.resultat &&
            grunnlag == other.grunnlag
    }

    companion object {
        fun create(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            resultat: Resultat,
            grunnlag: Pensjonsgrunnlag?,
            periode: Periode,
        ): VurderingsperiodePensjon {
            return tryCreate(id, opprettet, resultat, grunnlag, periode).getOrHandle {
                throw IllegalArgumentException(it.toString())
            }
        }

        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            resultat: Resultat,
            grunnlag: Pensjonsgrunnlag?,
            vurderingsperiode: Periode,
        ): Either<KunneIkkeLagePensjonsVilkår.Vurderingsperiode, VurderingsperiodePensjon> {

            grunnlag?.let {
                if (vurderingsperiode != it.periode) return KunneIkkeLagePensjonsVilkår.Vurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig.left()
            }

            return VurderingsperiodePensjon(
                id = id,
                opprettet = opprettet,
                resultat = resultat,
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
