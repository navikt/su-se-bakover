package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import java.util.UUID

/**
Et inngangsvilkår er de vilkårene som kan føre til avslag før det beregnes?

Her har vi utelatt for høy inntekt (SU<0) og su under minstegrense (SU<2%)
 */
sealed class Inngangsvilkår {
    object Uførhet : Inngangsvilkår()
    object Flyktning : Inngangsvilkår()
    object Oppholdstillatelse : Inngangsvilkår()
    object PersonligOppmøte : Inngangsvilkår()
    object Formue : Inngangsvilkår()
    object BorOgOppholderSegINorge : Inngangsvilkår()
    object UtenlandsoppholdOver90Dager : Inngangsvilkår()
    object InnlagtPåInstitusjon : Inngangsvilkår()
}

data class Vilkårsvurderinger(
    val uføre: Vilkår<Grunnlag.Uføregrunnlag> = Vilkår.IkkeVurdert.Uførhet,
) {
    companion object {
        val EMPTY = Vilkårsvurderinger()
    }
}

/**
 * vilkårsvurderinger - inneholder vilkårsvurdering av alle grunnlagstyper
 * vilkårsvurdering - aggregert vilkårsvurdering for en enkelt type grunnlag inneholder flere vurderingsperioder (en periode per grunnlag)
 * vurderingsperiode - inneholder vilkårsvurdering for ett enkelt grunnlag (kan være manuell (kan vurderes uten grunnlag) eller automatisk (har alltid grunnlag))
 * grunnlag - informasjon for en spesifikk periode som forteller noe om oppfyllelsen av et vilkår
 */

sealed class Vilkårsvurderingsresultat {
    data class Avslag(
        val vilkår: Set<Vilkår<*>>,
    ) : Vilkårsvurderingsresultat()

    data class Innvilget(
        val vilkår: Set<Vilkår<*>>,
    ) : Vilkårsvurderingsresultat()

    data class Uavklart(
        val vilkår: Set<Inngangsvilkår>,
    ) : Vilkårsvurderingsresultat()
}

/**
 * Vurderingen av et vilkår mot en eller flere grunnlagsdata
 */
sealed class Vilkår<T : Grunnlag> {

    sealed class IkkeVurdert<T : Grunnlag> : Vilkår<T>() {
        object Uførhet : Vilkår<Grunnlag.Uføregrunnlag>()
    }

    sealed class Vurdert<T : Grunnlag> : Vilkår<T>() {
        abstract val vilkår: Inngangsvilkår
        abstract val grunnlag: List<T>
        abstract val vurderingsperioder: List<Vurderingsperiode<T>>

        val resultat: Resultat by lazy {
            if (erInnvilget) Resultat.Innvilget else if (erAvslag) Resultat.Avslag else Resultat.Uavklart
        }

        val erInnvilget: Boolean by lazy {
            vurderingsperioder.all { it.resultat == Resultat.Innvilget }
        }

        val erAvslag: Boolean by lazy {
            vurderingsperioder.any { it.resultat == Resultat.Avslag }
        }

        data class Uførhet(
            override val vurderingsperioder: List<Vurderingsperiode<Grunnlag.Uføregrunnlag>>,
        ) : Vurdert<Grunnlag.Uføregrunnlag>() {
            override val vilkår = Inngangsvilkår.Uførhet
            override val grunnlag: List<Grunnlag.Uføregrunnlag> = vurderingsperioder.mapNotNull {
                it.grunnlag
            }
        }
    }
}

sealed class Vurderingsperiode<T : Grunnlag> : KanPlasseresPåTidslinje<Vurderingsperiode<T>> {
    abstract val id: UUID
    abstract override val opprettet: Tidspunkt
    abstract val resultat: Resultat
    abstract val grunnlag: T?
    abstract override val periode: Periode
    abstract val begrunnelse: String?

    data class Manuell<T : Grunnlag> private constructor(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val resultat: Resultat,
        override val grunnlag: T?,
        override val periode: Periode,
        override val begrunnelse: String?,
    ) : Vurderingsperiode<T>() {

        @Suppress("UNCHECKED_CAST")
        override fun copy(args: CopyArgs.Tidslinje): Manuell<T> = when (args) {
            CopyArgs.Tidslinje.Full -> {
                this.copy(id = UUID.randomUUID())
            }
            is CopyArgs.Tidslinje.NyPeriode -> {
                this.copy(
                    id = UUID.randomUUID(),
                    periode = args.periode,
                    grunnlag = grunnlag?.copy(args) as T,
                )
            }
        }

        companion object {
            fun <T : Grunnlag> create(
                id: UUID = UUID.randomUUID(),
                opprettet: Tidspunkt = Tidspunkt.now(),
                resultat: Resultat,
                grunnlag: T?,
                periode: Periode,
                begrunnelse: String?,
            ): Manuell<T> {
                return tryCreate(id, opprettet, resultat, grunnlag, periode, begrunnelse).getOrHandle {
                    throw IllegalArgumentException(it.toString())
                }
            }

            fun <T : Grunnlag> tryCreate(
                id: UUID = UUID.randomUUID(),
                opprettet: Tidspunkt = Tidspunkt.now(),
                resultat: Resultat,
                grunnlag: T?,
                periode: Periode,
                begrunnelse: String?,
            ): Either<UgyldigVurderingsperiode, Manuell<T>> {

                grunnlag?.let {
                    if (periode != it.periode) return UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig.left()
                }

                return Manuell(
                    id = id,
                    opprettet = opprettet,
                    resultat = resultat,
                    grunnlag = grunnlag,
                    periode = periode,
                    begrunnelse = begrunnelse,
                ).right()
            }
        }

        sealed class UgyldigVurderingsperiode {
            object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigVurderingsperiode()
        }
    }
}

sealed class Resultat {
    object Avslag : Resultat()
    object Innvilget : Resultat()
    object Uavklart : Resultat()
}
