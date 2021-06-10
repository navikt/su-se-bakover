package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import org.jetbrains.annotations.TestOnly
import java.time.LocalDate
import java.util.UUID

/**
Et inngangsvilkår er de vilkårene som kan føre til avslag før det beregnes?

Her har vi utelatt for høy inntekt (SU<0) og su under minstegrense (SU<2%)
 */
sealed class Inngangsvilkår {
    object Uførhet : Inngangsvilkår()
    object Formue : Inngangsvilkår()
    /*
     object Flyktning : Inngangsvilkår()
     object Oppholdstillatelse : Inngangsvilkår()
     object PersonligOppmøte : Inngangsvilkår()
     object BorOgOppholderSegINorge : Inngangsvilkår()
     object UtenlandsoppholdOver90Dager : Inngangsvilkår()
     object InnlagtPåInstitusjon : Inngangsvilkår()
     */

    fun tilOpphørsgrunn() = when (this) {
        Uførhet -> Opphørsgrunn.UFØRHET
        Formue -> Opphørsgrunn.FORMUE
    }
}

data class Vilkårsvurderinger(
    val uføre: Vilkår<Grunnlag.Uføregrunnlag?> = Vilkår.IkkeVurdert.Uførhet,
    val formue: Vilkår<Formuegrunnlag?> = Vilkår.IkkeVurdert.Formue,
) {
    private val vilkår = setOf(uføre, formue)

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Vilkårsvurderinger =
        this.copy(
            uføre = this.uføre.oppdaterStønadsperiode(stønadsperiode),
            formue = this.formue.oppdaterStønadsperiode(stønadsperiode),
        )

    companion object {
        val EMPTY = Vilkårsvurderinger()
    }

    val grunnlagsdata: Grunnlagsdata =
        Grunnlagsdata(
            uføregrunnlag = (uføre as? Vilkår.Vurdert.Uførhet)?.grunnlag ?: emptyList(),
            // formuegrunnlag = (formue as? Vilkår.Vurdert.Formue)?.grunnlag ?: emptyList(),
        )

    val resultat: Resultat by lazy {
        vilkår.map { it.resultat }.let { alleVurderingsresultat ->
            when {
                alleVurderingsresultat.all { it is Resultat.Innvilget } -> Resultat.Innvilget
                alleVurderingsresultat.any { it is Resultat.Avslag } -> Resultat.Avslag
                else -> Resultat.Uavklart
            }
        }
    }

    fun tidligsteDatoForAvslag(): LocalDate? {
        return vilkår.filterIsInstance<Vilkår.Vurdert<*>>()
            .flatMap { it.vurderingsperioder }
            .filter { it.resultat == Resultat.Avslag }
            .minByOrNull { it.periode.fraOgMed }?.periode?.fraOgMed
    }

    fun utledOpphørsgrunner(): List<Opphørsgrunn> {
        return vilkår.mapNotNull {
            when (it) {
                is Vilkår.IkkeVurdert.Uførhet -> null
                is Vilkår.Vurdert.Uførhet -> if (it.erAvslag) it.vilkår.tilOpphørsgrunn() else null
                is Vilkår.IkkeVurdert.Formue -> null
                is Vilkår.Vurdert.Formue -> if (it.erAvslag) it.vilkår.tilOpphørsgrunn() else null
            }
        }
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
sealed class Vilkår<T : Grunnlag?> {
    abstract fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Vilkår<T>

    sealed class IkkeVurdert<T : Grunnlag?> : Vilkår<T>() {
        object Uførhet : IkkeVurdert<Grunnlag.Uføregrunnlag?>() {
            override val resultat: Resultat = Resultat.Uavklart
            override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode) = this
        }

        object Formue : IkkeVurdert<Formuegrunnlag?>() {
            override val resultat: Resultat = Resultat.Uavklart
            override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode) = this
        }
    }

    abstract val resultat: Resultat

    sealed class Vurdert<T : Grunnlag?> : Vilkår<T>() {
        abstract val vilkår: Inngangsvilkår
        abstract val grunnlag: List<T>
        abstract val vurderingsperioder: List<Vurderingsperiode<T>>

        override val resultat: Resultat by lazy {
            if (erInnvilget) Resultat.Innvilget else if (erAvslag) Resultat.Avslag else Resultat.Uavklart
        }

        val erInnvilget: Boolean by lazy {
            vurderingsperioder.all { it.resultat == Resultat.Innvilget }
        }

        val erAvslag: Boolean by lazy {
            vurderingsperioder.any { it.resultat == Resultat.Avslag }
        }

        data class Formue private constructor(
            override val vurderingsperioder: Nel<Vurderingsperiode<Formuegrunnlag?>>,
        ) : Vurdert<Formuegrunnlag?>() {
            override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Vilkår<Formuegrunnlag?> =
                this.copy(
                    vurderingsperioder = this.vurderingsperioder.map {
                        it.oppdaterStønadsperiode(stønadsperiode)
                    },
                )

            override val vilkår = Inngangsvilkår.Formue
            override val grunnlag: List<Formuegrunnlag> = vurderingsperioder.mapNotNull {
                it.grunnlag
            }

            companion object {
                @TestOnly
                fun create(
                    vurderingsperioder: Nel<Vurderingsperiode<Formuegrunnlag?>>,
                ): Formue = tryCreate(vurderingsperioder).getOrHandle { throw IllegalArgumentException(it.toString()) }

                fun tryCreate(
                    vurderingsperioder: Nel<Vurderingsperiode<Formuegrunnlag?>>,
                ): Either<UgyldigFormuevilkår, Formue> {
                    if (vurderingsperioder.all { v1 ->
                        vurderingsperioder.minus(v1).any { v2 -> v1.periode overlapper v2.periode }
                    }
                    ) {
                        return UgyldigFormuevilkår.OverlappendeVurderingsperioder.left()
                    }
                    return Formue(vurderingsperioder).right()
                }
            }

            sealed class UgyldigFormuevilkår {
                object OverlappendeVurderingsperioder : UgyldigFormuevilkår()
            }
        }

        data class Uførhet private constructor(
            override val vurderingsperioder: Nel<Vurderingsperiode<Grunnlag.Uføregrunnlag?>>,
        ) : Vurdert<Grunnlag.Uføregrunnlag?>() {
            override val vilkår = Inngangsvilkår.Uførhet
            override val grunnlag: List<Grunnlag.Uføregrunnlag> = vurderingsperioder.mapNotNull {
                it.grunnlag
            }

            companion object {
                @TestOnly
                fun create(
                    vurderingsperioder: Nel<Vurderingsperiode<Grunnlag.Uføregrunnlag?>>,
                ): Uførhet = tryCreate(vurderingsperioder).getOrHandle { throw IllegalArgumentException(it.toString()) }

                fun tryCreate(
                    vurderingsperioder: Nel<Vurderingsperiode<Grunnlag.Uføregrunnlag?>>,
                ): Either<UgyldigUførevilkår, Uførhet> {
                    if (vurderingsperioder.all { v1 ->
                        vurderingsperioder.minus(v1).any { v2 -> v1.periode overlapper v2.periode }
                    }
                    ) {
                        return UgyldigUførevilkår.OverlappendeVurderingsperioder.left()
                    }
                    return Uførhet(vurderingsperioder).right()
                }
            }

            sealed class UgyldigUførevilkår {
                object OverlappendeVurderingsperioder : UgyldigUførevilkår()
            }

            override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Vilkår<Grunnlag.Uføregrunnlag?> =
                this.copy(
                    vurderingsperioder = this.vurderingsperioder.map {
                        it.oppdaterStønadsperiode(stønadsperiode)
                    },
                )
        }
    }
}

sealed class Vurderingsperiode<T : Grunnlag?> : KanPlasseresPåTidslinje<Vurderingsperiode<T>> {
    abstract fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Vurderingsperiode<T>
    abstract val id: UUID
    abstract override val opprettet: Tidspunkt
    abstract val resultat: Resultat
    abstract val grunnlag: T?
    abstract override val periode: Periode
    abstract val begrunnelse: String?

    data class Uføre private constructor(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val resultat: Resultat,
        override val grunnlag: Grunnlag.Uføregrunnlag?,
        override val periode: Periode,
        override val begrunnelse: String?,
    ) : Vurderingsperiode<Grunnlag.Uføregrunnlag?>() {

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Uføre {
            return this.copy(
                periode = stønadsperiode.periode,
                grunnlag = this.grunnlag?.oppdaterPeriode(stønadsperiode.periode),
            )
        }

        override fun copy(args: CopyArgs.Tidslinje): Uføre = when (args) {
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

        companion object {
            fun create(
                id: UUID = UUID.randomUUID(),
                opprettet: Tidspunkt = Tidspunkt.now(),
                resultat: Resultat,
                grunnlag: Grunnlag.Uføregrunnlag?,
                periode: Periode,
                begrunnelse: String?,
            ): Uføre {
                return tryCreate(id, opprettet, resultat, grunnlag, periode, begrunnelse).getOrHandle {
                    throw IllegalArgumentException(it.toString())
                }
            }

            fun tryCreate(
                id: UUID = UUID.randomUUID(),
                opprettet: Tidspunkt = Tidspunkt.now(),
                resultat: Resultat,
                grunnlag: Grunnlag.Uføregrunnlag?,
                vurderingsperiode: Periode,
                begrunnelse: String?,
            ): Either<UgyldigVurderingsperiode, Uføre> {

                grunnlag?.let {
                    if (vurderingsperiode != it.periode) return UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig.left()
                }

                return Uføre(
                    id = id,
                    opprettet = opprettet,
                    resultat = resultat,
                    grunnlag = grunnlag,
                    periode = vurderingsperiode,
                    begrunnelse = begrunnelse,
                ).right()
            }
        }

        sealed class UgyldigVurderingsperiode {
            object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigVurderingsperiode()
        }
    }

    data class Formue private constructor(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt = Tidspunkt.now(),
        override val resultat: Resultat,
        override val grunnlag: Formuegrunnlag?,
        override val periode: Periode,
        override val begrunnelse: String?,
    ) : Vurderingsperiode<Formuegrunnlag?>() {

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Formue {
            return this.copy(
                periode = stønadsperiode.periode,
                grunnlag = this.grunnlag?.oppdaterPeriode(stønadsperiode.periode),
            )
        }

        override fun copy(args: CopyArgs.Tidslinje): Formue = when (args) {
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

        companion object {
            fun create(
                id: UUID = UUID.randomUUID(),
                opprettet: Tidspunkt = Tidspunkt.now(),
                resultat: Resultat,
                grunnlag: Formuegrunnlag?,
                periode: Periode,
                begrunnelse: String?,
            ): Formue {
                return tryCreate(id, opprettet, resultat, grunnlag, periode, begrunnelse).getOrHandle {
                    throw IllegalArgumentException(it.toString())
                }
            }

            fun tryCreate(
                id: UUID = UUID.randomUUID(),
                opprettet: Tidspunkt = Tidspunkt.now(),
                resultat: Resultat,
                grunnlag: Formuegrunnlag?,
                vurderingsperiode: Periode,
                begrunnelse: String?,
            ): Either<UgyldigVurderingsperiode, Formue> {

                grunnlag?.let {
                    if (vurderingsperiode != it.periode) return UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig.left()
                }

                return Formue(
                    id = id,
                    opprettet = opprettet,
                    resultat = resultat,
                    grunnlag = grunnlag,
                    periode = vurderingsperiode,
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
