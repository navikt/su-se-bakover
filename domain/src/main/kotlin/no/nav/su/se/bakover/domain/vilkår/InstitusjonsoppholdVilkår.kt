package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.harOverlappende
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.grunnlag.InstitusjonsoppholdGrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import java.time.LocalDate
import java.util.UUID

sealed class InstitusjonsoppholdVilkår : Vilkår() {
    override val vilkår = Inngangsvilkår.Institusjonsopphold
    abstract val grunnlag: List<InstitusjonsoppholdGrunnlag>

    abstract override fun lagTidslinje(periode: Periode): InstitusjonsoppholdVilkår
    abstract fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): InstitusjonsoppholdVilkår
    abstract override fun slåSammenLikePerioder(): InstitusjonsoppholdVilkår

    object IkkeVurdert : InstitusjonsoppholdVilkår() {
        override val resultat: Resultat = Resultat.Uavklart
        override val erAvslag = false
        override val erInnvilget = false
        override val grunnlag = emptyList<InstitusjonsoppholdGrunnlag>()
        override fun lagTidslinje(periode: Periode): InstitusjonsoppholdVilkår {
            return this
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun slåSammenLikePerioder(): InstitusjonsoppholdVilkår {
            return this
        }

        override fun hentTidligesteDatoForAvslag(): LocalDate? = null
        override fun erLik(other: Vilkår): Boolean {
            return other is IkkeVurdert
        }
    }

    data class Vurdert private constructor(
        val vurderingsperioder: Nel<VurderingsperiodeInstitusjonsopphold>,
    ) : InstitusjonsoppholdVilkår() {

        override val grunnlag: List<InstitusjonsoppholdGrunnlag> = vurderingsperioder.mapNotNull { it.grunnlag }
        override fun lagTidslinje(periode: Periode): InstitusjonsoppholdVilkår {
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

        companion object {
            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodeInstitusjonsopphold>,
            ): Either<UgyldigInstitisjonsoppholdVilkår, Vurdert> {
                if (vurderingsperioder.harOverlappende()) {
                    return UgyldigInstitisjonsoppholdVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder.kronologisk()).right()
            }
        }

        sealed class UgyldigInstitisjonsoppholdVilkår {
            object OverlappendeVurderingsperioder : UgyldigInstitisjonsoppholdVilkår()
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): InstitusjonsoppholdVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn èn vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }

        override fun slåSammenLikePerioder(): InstitusjonsoppholdVilkår {
            return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
        }
    }
}

data class VurderingsperiodeInstitusjonsopphold private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val resultat: Resultat,
    override val grunnlag: InstitusjonsoppholdGrunnlag?,
    override val periode: Periode,
) : Vurderingsperiode(), KanPlasseresPåTidslinje<VurderingsperiodeInstitusjonsopphold> {

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodeInstitusjonsopphold {
        return create(
            id = id,
            opprettet = opprettet,
            resultat = resultat,
            periode = stønadsperiode.periode,
            grunnlag = this.grunnlag?.oppdaterPeriode(stønadsperiode.periode),
        )
    }

    override fun copy(args: CopyArgs.Tidslinje): VurderingsperiodeInstitusjonsopphold = when (args) {
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
        return other is VurderingsperiodeInstitusjonsopphold &&
            resultat == other.resultat &&
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
            resultat: Resultat,
            grunnlag: InstitusjonsoppholdGrunnlag?,
            periode: Periode,
        ): VurderingsperiodeInstitusjonsopphold {
            return tryCreate(id, opprettet, resultat, grunnlag, periode).getOrHandle {
                throw IllegalArgumentException(it.toString())
            }
        }

        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            resultat: Resultat,
            grunnlag: InstitusjonsoppholdGrunnlag?,
            vurderingsperiode: Periode,
        ): Either<UgyldigVurderingsperiode, VurderingsperiodeInstitusjonsopphold> {

            grunnlag?.let {
                if (vurderingsperiode != it.periode) return UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig.left()
            }

            return VurderingsperiodeInstitusjonsopphold(
                id = id,
                opprettet = opprettet,
                resultat = resultat,
                grunnlag = grunnlag,
                periode = vurderingsperiode,
            ).right()
        }
    }

    sealed class UgyldigVurderingsperiode {
        object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigVurderingsperiode()
    }
}
