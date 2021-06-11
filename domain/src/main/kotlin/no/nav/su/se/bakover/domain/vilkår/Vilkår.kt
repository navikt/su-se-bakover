package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.Grunnbeløp
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
    val uføre: Vilkår.Uførhet = Vilkår.Uførhet.IkkeVurdert,
    val formue: Vilkår.Formue = Vilkår.Formue.IkkeVurdert,
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
            uføregrunnlag = (uføre as? Vilkår.Uførhet.Vurdert)?.grunnlag ?: emptyList(),
            // formuegrunnlag = (formue as? Vilkår.Formue.Vurdert)?.grunnlag ?: emptyList(),
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

    fun tidligsteDatoForAvslag(): LocalDate? = vilkår.mapNotNull { it.hentTidligesteDatoForAvslag() }.minByOrNull { it }

    fun utledOpphørsgrunner(): List<Opphørsgrunn> {
        return vilkår.mapNotNull {
            when (it) {
                // the new bois
                is Vilkår.Uførhet.Vurdert -> if (it.erAvslag) it.vilkår.tilOpphørsgrunn() else null
                is Vilkår.Formue.Vurdert -> if (it.erAvslag) it.vilkår.tilOpphørsgrunn() else null
                Vilkår.Formue.IkkeVurdert, Vilkår.Uførhet.IkkeVurdert -> null
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
        val vilkår: Set<Vilkår>,
    ) : Vilkårsvurderingsresultat()

    data class Innvilget(
        val vilkår: Set<Vilkår>,
    ) : Vilkårsvurderingsresultat()

    data class Uavklart(
        val vilkår: Set<Inngangsvilkår>,
    ) : Vilkårsvurderingsresultat()
}

/**
 * Vurderingen av et vilkår mot en eller flere grunnlagsdata
 */
sealed class Vilkår {
    abstract val resultat: Resultat
    abstract val erAvslag: Boolean
    abstract val erInnvilget: Boolean

    abstract fun hentTidligesteDatoForAvslag(): LocalDate?

    sealed class Uførhet : Vilkår() {
        abstract fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Uførhet
        object IkkeVurdert : Uførhet() {
            override val resultat: Resultat = Resultat.Uavklart
            override val erAvslag = false
            override val erInnvilget = false

            override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
            override fun hentTidligesteDatoForAvslag(): LocalDate? = null
        }

        data class Vurdert private constructor(
            val vurderingsperioder: Nel<Vurderingsperiode<Grunnlag.Uføregrunnlag?>>,
        ) : Uførhet() {
            val vilkår = Inngangsvilkår.Uførhet
            val grunnlag: List<Grunnlag.Uføregrunnlag> = vurderingsperioder.mapNotNull {
                it.grunnlag
            }

            override val resultat: Resultat by lazy {
                if (erInnvilget) Resultat.Innvilget else if (erAvslag) Resultat.Avslag else Resultat.Uavklart
            }

            override val erInnvilget: Boolean by lazy {
                vurderingsperioder.all { it.resultat == Resultat.Innvilget }
            }

            override val erAvslag: Boolean by lazy {
                vurderingsperioder.any { it.resultat == Resultat.Avslag }
            }

            override fun hentTidligesteDatoForAvslag(): LocalDate? {
                return vurderingsperioder.filter { it.resultat == Resultat.Avslag }.map { it.periode.fraOgMed }
                    .minByOrNull { it }
            }

            companion object {
                @TestOnly
                fun create(
                    vurderingsperioder: Nel<Vurderingsperiode<Grunnlag.Uføregrunnlag?>>,
                ): Vurdert = tryCreate(vurderingsperioder).getOrHandle { throw IllegalArgumentException(it.toString()) }

                fun tryCreate(
                    vurderingsperioder: Nel<Vurderingsperiode<Grunnlag.Uføregrunnlag?>>,
                ): Either<UgyldigUførevilkår, Vurdert> {
                    if (vurderingsperioder.all { v1 ->
                        vurderingsperioder.minus(v1).any { v2 -> v1.periode overlapper v2.periode }
                    }
                    ) {
                        return UgyldigUførevilkår.OverlappendeVurderingsperioder.left()
                    }
                    return Vurdert(vurderingsperioder).right()
                }
            }

            sealed class UgyldigUførevilkår {
                object OverlappendeVurderingsperioder : UgyldigUførevilkår()
            }

            override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Uførhet =
                this.copy(
                    vurderingsperioder = this.vurderingsperioder.map {
                        it.oppdaterStønadsperiode(stønadsperiode)
                    },
                )
        }
    }

    sealed class Formue : Vilkår() {
        abstract fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Formue
        object IkkeVurdert : Formue() {
            override val resultat: Resultat = Resultat.Uavklart
            override val erAvslag = false
            override val erInnvilget = false

            override fun hentTidligesteDatoForAvslag(): LocalDate? = null
            override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Formue = this
        }

        data class Vurdert private constructor(
            val vurderingsperioder: Nel<Vurderingsperiode<Formuegrunnlag?>>,
        ) : Formue() {
            override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Formue =
                this.copy(
                    vurderingsperioder = this.vurderingsperioder.map {
                        it.oppdaterStønadsperiode(stønadsperiode)
                    },
                )

            override val resultat: Resultat by lazy {
                if (erInnvilget) Resultat.Innvilget else if (erAvslag) Resultat.Avslag else Resultat.Uavklart
            }

            override val erInnvilget: Boolean by lazy {
                vurderingsperioder.all { it.resultat == Resultat.Innvilget }
            }

            override val erAvslag: Boolean by lazy {
                vurderingsperioder.any { it.resultat == Resultat.Avslag }
            }

            override fun hentTidligesteDatoForAvslag(): LocalDate? {
                return vurderingsperioder.filter { it.resultat == Resultat.Avslag }.map { it.periode.fraOgMed }
                    .minByOrNull { it }
            }

            val vilkår = Inngangsvilkår.Formue
            val grunnlag: List<Formuegrunnlag> = vurderingsperioder.mapNotNull {
                it.grunnlag
            }

            companion object {
                @TestOnly
                fun create(
                    grunnlag: Nel<Formuegrunnlag>,
                ): Vurdert = tryCreate(grunnlag).getOrHandle { throw IllegalArgumentException(it.toString()) }

                fun tryCreate(
                    grunnlag: Nel<Formuegrunnlag>,
                ): Either<UgyldigFormuevilkår, Vurdert> {
                    val vurderingsperioder = grunnlag.map {
                        Vurderingsperiode.Formue.tryCreate(it)
                    }
                    return fromVurderingsperioder(vurderingsperioder)
                }

                /**
                 * Skal kun kalles fra persistence-laget
                 */
                fun fromPersistence(
                    vurderingsperioder: Nel<Vurderingsperiode<Formuegrunnlag?>>,
                ): Vurdert =
                    fromVurderingsperioder(vurderingsperioder).getOrHandle { throw IllegalArgumentException(it.toString()) }

                private fun fromVurderingsperioder(
                    vurderingsperioder: Nel<Vurderingsperiode<Formuegrunnlag?>>,
                ): Either<UgyldigFormuevilkår, Vurdert> {
                    if (vurderingsperioder.all { v1 ->
                        vurderingsperioder.minus(v1).any { v2 -> v1.periode overlapper v2.periode }
                    }
                    ) {
                        return UgyldigFormuevilkår.OverlappendeVurderingsperioder.left()
                    }
                    return Vurdert(vurderingsperioder).right()
                }
            }

            sealed class UgyldigFormuevilkår {
                object OverlappendeVurderingsperioder : UgyldigFormuevilkår()
            }
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

            fun tryCreate(
                grunnlag: Formuegrunnlag,
            ): Formue {
                return Formue(
                    id = UUID.randomUUID(),
                    opprettet = grunnlag.opprettet,
                    // TODO jah: Nå tar vi første måned, men denne kan forandre seg ila. perioden
                    resultat = if (grunnlag.sumFormue() <= Grunnbeløp.`0,5G`.fraDato(grunnlag.periode.fraOgMed)) Resultat.Innvilget else Resultat.Avslag,
                    grunnlag = grunnlag,
                    periode = grunnlag.periode,
                    // TODO jah: Skal vi flytte begrunnelsen ut av grunnlaget?
                    begrunnelse = grunnlag.begrunnelse,
                )
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
