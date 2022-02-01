package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.minAndMaxOf
import no.nav.su.se.bakover.common.periode.overlappende
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.grunnlag.Utenlandsoppholdgrunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeUtenlandsopphold.Companion.slåSammenVurderingsperioder
import java.time.LocalDate
import java.util.UUID

sealed class UtenlandsoppholdVilkår : Vilkår() {
    override val vilkår = Inngangsvilkår.Utenlandsopphold
    abstract val grunnlag: List<Utenlandsoppholdgrunnlag>

    abstract override fun lagTidslinje(periode: Periode): UtenlandsoppholdVilkår
    abstract override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): UtenlandsoppholdVilkår

    object IkkeVurdert : UtenlandsoppholdVilkår() {
        override val resultat: Resultat = Resultat.Uavklart
        override val erAvslag = false
        override val erInnvilget = false
        override val grunnlag = emptyList<Utenlandsoppholdgrunnlag>()
        override fun lagTidslinje(periode: Periode): UtenlandsoppholdVilkår {
            return this
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
        override fun hentTidligesteDatoForAvslag(): LocalDate? = null
        override fun erLik(other: Vilkår): Boolean {
            return other is IkkeVurdert
        }
    }

    data class Vurdert private constructor(
        val vurderingsperioder: Nel<VurderingsperiodeUtenlandsopphold>,
    ) : UtenlandsoppholdVilkår() {

        override val grunnlag: List<Utenlandsoppholdgrunnlag> = vurderingsperioder.mapNotNull { it.grunnlag }
        override fun lagTidslinje(periode: Periode): UtenlandsoppholdVilkår {
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

        fun slåSammenVurderingsperioder(): Either<UgyldigUtenlandsoppholdVilkår, UtenlandsoppholdVilkår> {
            return tryCreateFromVurderingsperioder(vurderingsperioder = vurderingsperioder.slåSammenVurderingsperioder())
        }

        companion object {
            fun tryCreate(
                vurderingsperioder: Nel<VurderingsperiodeUtenlandsopphold>,
            ): Either<UgyldigUtenlandsoppholdVilkår, Vurdert> {
                if (vurderingsperioder.overlappende()) {
                    return UgyldigUtenlandsoppholdVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder).right()
            }

            fun createFromVilkårsvurderinger(
                vurderingsperioder: Nel<VurderingsperiodeUtenlandsopphold>,
            ): Vurdert =
                tryCreateFromVurderingsperioder(vurderingsperioder).getOrHandle { throw IllegalArgumentException(it.toString()) }

            fun tryCreateFromVurderingsperioder(
                vurderingsperioder: Nel<VurderingsperiodeUtenlandsopphold>,
            ): Either<UgyldigUtenlandsoppholdVilkår, Vurdert> {
                if (vurderingsperioder.overlappende()) {
                    return UgyldigUtenlandsoppholdVilkår.OverlappendeVurderingsperioder.left()
                }
                return Vurdert(vurderingsperioder).right()
            }
        }

        sealed class UgyldigUtenlandsoppholdVilkår {
            object OverlappendeVurderingsperioder : UgyldigUtenlandsoppholdVilkår()
        }

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): UtenlandsoppholdVilkår {
            check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn èn vurdering" }
            return copy(
                vurderingsperioder = vurderingsperioder.map {
                    it.oppdaterStønadsperiode(stønadsperiode)
                },
            )
        }
    }
}

data class VurderingsperiodeUtenlandsopphold private constructor(
    override val id: UUID = UUID.randomUUID(),
    override val opprettet: Tidspunkt,
    override val resultat: Resultat,
    override val grunnlag: Utenlandsoppholdgrunnlag?,
    override val periode: Periode,
    val begrunnelse: String?,
) : Vurderingsperiode(), KanPlasseresPåTidslinje<VurderingsperiodeUtenlandsopphold> {

    override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): VurderingsperiodeUtenlandsopphold {
        return create(
            id = id,
            opprettet = opprettet,
            resultat = resultat,
            periode = stønadsperiode.periode,
            grunnlag = this.grunnlag?.oppdaterPeriode(stønadsperiode.periode),
            begrunnelse = begrunnelse,
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
            grunnlag: Utenlandsoppholdgrunnlag?,
            periode: Periode,
            begrunnelse: String?,
        ): VurderingsperiodeUtenlandsopphold {
            return tryCreate(id, opprettet, resultat, grunnlag, periode, begrunnelse).getOrHandle {
                throw IllegalArgumentException(it.toString())
            }
        }

        fun tryCreate(
            id: UUID = UUID.randomUUID(),
            opprettet: Tidspunkt,
            resultat: Resultat,
            grunnlag: Utenlandsoppholdgrunnlag?,
            vurderingsperiode: Periode,
            begrunnelse: String?,
        ): Either<UgyldigVurderingsperiode, VurderingsperiodeUtenlandsopphold> {

            grunnlag?.let {
                if (vurderingsperiode != it.periode) return UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig.left()
            }

            return VurderingsperiodeUtenlandsopphold(
                id = id,
                opprettet = opprettet,
                resultat = resultat,
                grunnlag = grunnlag,
                periode = vurderingsperiode,
                begrunnelse = begrunnelse,
            ).right()
        }

        fun Nel<VurderingsperiodeUtenlandsopphold>.slåSammenVurderingsperioder(): Nel<VurderingsperiodeUtenlandsopphold> {
            val slåttSammen = this.sortedBy { it.periode.fraOgMed }
                .fold(mutableListOf<MutableList<VurderingsperiodeUtenlandsopphold>>()) { acc, utenlandsopphold ->
                    if (acc.isEmpty()) {
                        acc.add(mutableListOf(utenlandsopphold))
                    } else if (acc.last().sistePeriodeErLikOgTilstøtende(utenlandsopphold)) {
                        acc.last().add(utenlandsopphold)
                    } else {
                        acc.add(mutableListOf(utenlandsopphold))
                    }
                    acc
                }.map {
                    val periode = it.map { it.periode }.minAndMaxOf()
                    it.first().copy(CopyArgs.Tidslinje.NyPeriode(periode = periode))
                }
            return NonEmptyList.fromListUnsafe(slåttSammen)
        }

        private fun List<VurderingsperiodeUtenlandsopphold>.sistePeriodeErLikOgTilstøtende(other: VurderingsperiodeUtenlandsopphold) =
            this.last().tilstøterOgErLik(other)
    }

    sealed class UgyldigVurderingsperiode {
        object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigVurderingsperiode()
    }
}
