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
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.Grunnbeløp.Companion.`0,5G`
import no.nav.su.se.bakover.domain.behandling.avslag.Opphørsgrunn
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
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
    val uføre: Vilkår.Uførhet,
    val formue: Vilkår.Formue = Vilkår.Formue.IkkeVurdert,
) {

    // TODO jah: Valider at de vurderte uføre-periodene er de samme som de vurderte formue-periodene
    private val vilkår = setOf(uføre, formue)

    fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Vilkårsvurderinger =
        this.copy(
            uføre = this.uføre.oppdaterStønadsperiode(stønadsperiode),
            formue = this.formue.oppdaterStønadsperiode(stønadsperiode),
        )

    companion object {
        val IkkeVurdert = Vilkårsvurderinger(
            uføre = Vilkår.Uførhet.IkkeVurdert,
            formue = Vilkår.Formue.IkkeVurdert,
        )
    }

    val resultat: Resultat =
        vilkår.map { it.resultat }.let { alleVurderingsresultat ->
            when {
                alleVurderingsresultat.all { it is Resultat.Innvilget } -> Resultat.Innvilget
                alleVurderingsresultat.any { it is Resultat.Avslag } -> Resultat.Avslag
                else -> Resultat.Uavklart
            }
        }

    fun tidligsteDatoForAvslag(): LocalDate? = vilkår.mapNotNull { it.hentTidligesteDatoForAvslag() }.minByOrNull { it }

    fun utledOpphørsgrunner(): List<Opphørsgrunn> {
        return vilkår.mapNotNull {
            when (it) {
                is Vilkår.Uførhet.Vurdert -> if (it.erAvslag) it.vilkår.tilOpphørsgrunn() else null
                is Vilkår.Formue.Vurdert -> if (it.erAvslag) it.vilkår.tilOpphørsgrunn() else null
                Vilkår.Formue.IkkeVurdert, Vilkår.Uførhet.IkkeVurdert -> null
            }
        }
    }

    val periode: Periode? by lazy {
        val uføreperioder = (uføre as? Vilkår.Uførhet.Vurdert)?.vurderingsperioder?.map { it.periode } ?: emptyList()
        val formueperioder = (formue as? Vilkår.Formue.Vurdert)?.vurderingsperioder?.map { it.periode } ?: emptyList()
        uføreperioder.plus(formueperioder).ifNotEmpty {
            Periode.create(
                fraOgMed = this.minOf { it.fraOgMed },
                tilOgMed = this.maxOf { it.tilOgMed },
            )
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
        abstract val grunnlag: List<Grunnlag.Uføregrunnlag>

        abstract fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Uførhet

        object IkkeVurdert : Uførhet() {
            override val resultat: Resultat = Resultat.Uavklart
            override val erAvslag = false
            override val erInnvilget = false
            override val grunnlag = emptyList<Grunnlag.Uføregrunnlag>()

            override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this
            override fun hentTidligesteDatoForAvslag(): LocalDate? = null
        }

        data class Vurdert private constructor(
            val vurderingsperioder: Nel<Vurderingsperiode.Uføre>,
        ) : Uførhet() {
            val vilkår = Inngangsvilkår.Uførhet
            override val grunnlag: List<Grunnlag.Uføregrunnlag> = vurderingsperioder.mapNotNull {
                it.grunnlag
            }

            override val erInnvilget: Boolean =
                vurderingsperioder.all { it.resultat == Resultat.Innvilget }

            override val erAvslag: Boolean =
                vurderingsperioder.any { it.resultat == Resultat.Avslag }

            override val resultat: Resultat =
                if (erInnvilget) Resultat.Innvilget else if (erAvslag) Resultat.Avslag else Resultat.Uavklart

            override fun hentTidligesteDatoForAvslag(): LocalDate? {
                return vurderingsperioder.filter { it.resultat == Resultat.Avslag }.map { it.periode.fraOgMed }
                    .minByOrNull { it }
            }

            companion object {

                fun tryCreate(
                    vurderingsperioder: Nel<Vurderingsperiode.Uføre>,
                ): Either<UgyldigUførevilkår, Vurdert> {
                    if (vurderingsperioder.all { v1 ->
                        vurderingsperioder.minus(v1).any { v2 -> v1.periode overlapper v2.periode }
                    }
                    ) {
                        return UgyldigUførevilkår.OverlappendeVurderingsperioder.left()
                    }

                    return Vurdert(vurderingsperioder).right()
                }

                fun Nel<Vurderingsperiode.Uføre>.slåSammenVurderingsperiode(): Nel<Vurderingsperiode.Uføre> {
                    val slåttSammen = this.sortedBy { it.periode.fraOgMed }
                        .fold(mutableListOf<MutableList<Vurderingsperiode.Uføre>>()) { acc, uføre ->
                            if (acc.isEmpty()) {
                                acc.add(mutableListOf(uføre))
                            } else if (acc.last().sisteUføreperiodeErLikOgTilstøtende(uføre)) {
                                acc.last().add(uføre)
                            } else {
                                acc.add(mutableListOf(uføre))
                            }
                            acc
                        }.map {
                            val periode = it.map { it.periode }.minAndMaxOf()
                            it.first().copy(CopyArgs.Tidslinje.NyPeriode(periode = periode))
                        }
                    return NonEmptyList.fromListUnsafe(slåttSammen)
                }

                private fun List<Vurderingsperiode.Uføre>.sisteUføreperiodeErLikOgTilstøtende(other: Vurderingsperiode.Uføre) =
                    this.last().let { it.tilstøterOgErLik(other) }
            }

            sealed class UgyldigUførevilkår {
                object OverlappendeVurderingsperioder : UgyldigUførevilkår()
            }

            override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Uførhet {
                if (this.vurderingsperioder.size > 1) {
                    throw IllegalStateException("kan ikke oppdatere stønadsperiode for uføre vilkår som har mer enn èn vurdering")
                }
                return this.copy(
                    vurderingsperioder = this.vurderingsperioder.map {
                        it.oppdaterStønadsperiode(stønadsperiode)
                    },
                )
            }
        }
    }

    sealed class Formue : Vilkår() {

        abstract val grunnlag: List<Formuegrunnlag>

        abstract fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Formue

        /* § 8. Formue over 0,5 ganger grunnbeløpet gir avslag i søknaden. */
        val vilkår = Inngangsvilkår.Formue

        /**
         * Definert i paragraf 8 til 0.5 G som vanligvis endrer seg 1. mai, årlig.
         */
        val formuegrenser: List<Pair<LocalDate, Int>> =
            `0,5G`.gyldigPåDatoOgSenere(LocalDate.of(2021, 1, 1))

        object IkkeVurdert : Formue() {
            override val resultat: Resultat = Resultat.Uavklart
            override val erAvslag = false
            override val erInnvilget = false

            override fun hentTidligesteDatoForAvslag(): LocalDate? = null
            override val grunnlag = emptyList<Formuegrunnlag>()

            override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Formue = this
        }

        data class Vurdert private constructor(
            val vurderingsperioder: Nel<Vurderingsperiode.Formue>,
        ) : Formue() {
            override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Formue {
                if (this.vurderingsperioder.size > 1) {
                    throw IllegalStateException("kan ikke oppdatere stønadsperiode for formue vilkår som har mer enn èn vurdering")
                }
                return this.copy(
                    vurderingsperioder = this.vurderingsperioder.map {
                        it.oppdaterStønadsperiode(stønadsperiode)
                    },
                )
            }

            override val erInnvilget: Boolean =
                vurderingsperioder.all { it.resultat == Resultat.Innvilget }

            override val erAvslag: Boolean =
                vurderingsperioder.any { it.resultat == Resultat.Avslag }

            override val resultat: Resultat =
                if (erInnvilget) Resultat.Innvilget else if (erAvslag) Resultat.Avslag else Resultat.Uavklart

            override fun hentTidligesteDatoForAvslag(): LocalDate? {
                return vurderingsperioder.filter { it.resultat == Resultat.Avslag }.map { it.periode.fraOgMed }
                    .minByOrNull { it }
            }

            override val grunnlag: List<Formuegrunnlag> = vurderingsperioder.map {
                it.grunnlag
            }

            companion object {

                fun tryCreateFromGrunnlag(
                    grunnlag: Nel<Formuegrunnlag>,
                ): Either<UgyldigFormuevilkår, Vurdert> {
                    val vurderingsperioder = grunnlag.map {
                        Vurderingsperiode.Formue.tryCreateFromGrunnlag(it)
                    }
                    return fromVurderingsperioder(vurderingsperioder)
                }

                fun createFromVilkårsvurderinger(
                    vurderingsperioder: Nel<Vurderingsperiode.Formue>,
                ): Vurdert =
                    fromVurderingsperioder(vurderingsperioder).getOrHandle { throw IllegalArgumentException(it.toString()) }

                private fun fromVurderingsperioder(
                    vurderingsperioder: Nel<Vurderingsperiode.Formue>,
                ): Either<UgyldigFormuevilkår, Vurdert> {
                    if (vurderingsperioder.all { v1 ->
                        vurderingsperioder.minus(v1).any { v2 -> v1.periode overlapper v2.periode }
                    }
                    ) {
                        return UgyldigFormuevilkår.OverlappendeVurderingsperioder.left()
                    }
                    return Vurdert(vurderingsperioder).right()
                }

                fun Nel<Vurderingsperiode.Formue>.slåSammenVurderingsperiode(): Nel<Vurderingsperiode.Formue> {
                    val slåttSammen = this.sortedBy { it.periode.fraOgMed }
                        .fold(mutableListOf<MutableList<Vurderingsperiode.Formue>>()) { acc, formue ->
                            if (acc.isEmpty()) {
                                acc.add(mutableListOf(formue))
                            } else if (acc.last().sisteFormueperiodeErLikOgTilstøtende(formue)) {
                                acc.last().add(formue)
                            } else {
                                acc.add(mutableListOf(formue))
                            }
                            acc
                        }.map {
                            val periode = it.map { it.periode }.minAndMaxOf()
                            it.first().copy(CopyArgs.Tidslinje.NyPeriode(periode = periode))
                        }
                    return NonEmptyList.fromListUnsafe(slåttSammen)
                }

                private fun List<Vurderingsperiode.Formue>.sisteFormueperiodeErLikOgTilstøtende(other: Vurderingsperiode.Formue) =
                    this.last().let { it.tilstøterOgErLik(other) }
            }

            sealed class UgyldigFormuevilkår {
                object OverlappendeVurderingsperioder : UgyldigFormuevilkår()
            }
        }
    }
}

sealed class Vurderingsperiode {
    abstract fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Vurderingsperiode
    abstract val id: UUID
    abstract val opprettet: Tidspunkt
    abstract val resultat: Resultat
    abstract val grunnlag: Grunnlag?
    abstract val periode: Periode

    fun tilstøter(other: Vurderingsperiode): Boolean {
        return this.periode.tilstøter(other.periode)
    }

    /**
     * unnlater ID, og opprettet.
     */
    abstract fun erLik(other: Vurderingsperiode): Boolean

    fun tilstøterOgErLik(other: Vurderingsperiode) = this.tilstøter(other) && this.erLik(other)

    data class Uføre private constructor(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt,
        override val resultat: Resultat,
        override val grunnlag: Grunnlag.Uføregrunnlag?,
        override val periode: Periode,
        val begrunnelse: String?,
    ) : Vurderingsperiode(), KanPlasseresPåTidslinje<Uføre> {

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

        /**
         * Sjekker at uføregrad og forventet inntekt er lik
         */
        override fun erLik(other: Vurderingsperiode): Boolean {
            if (other !is Uføre) {
                return false
            }
            if ((this.grunnlag == null && other.grunnlag != null) || (this.grunnlag != null && other.grunnlag == null)) {
                return false
            }

            return this.resultat == other.resultat && (
                this.grunnlag == null && other.grunnlag == null || this.grunnlag!!.tilstøterOgErLik(
                    other.grunnlag!!,
                )
                )
        }

        companion object {
            fun create(
                id: UUID = UUID.randomUUID(),
                opprettet: Tidspunkt,
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
                opprettet: Tidspunkt,
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
        override val opprettet: Tidspunkt,
        override val resultat: Resultat,
        override val grunnlag: Formuegrunnlag,
        override val periode: Periode,
    ) : Vurderingsperiode(), KanPlasseresPåTidslinje<Formue> {

        override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Formue {
            return this.copy(
                periode = stønadsperiode.periode,
                grunnlag = this.grunnlag.oppdaterPeriode(stønadsperiode.periode),
            )
        }

        override fun copy(args: CopyArgs.Tidslinje): Formue = when (args) {
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
            if (other !is Formue) {
                return false
            }
            return this.resultat == other.resultat &&
                this.grunnlag.tilstøterOgErLik(other.grunnlag)
        }

        companion object {
            fun create(
                id: UUID = UUID.randomUUID(),
                opprettet: Tidspunkt,
                resultat: Resultat,
                grunnlag: Formuegrunnlag,
                periode: Periode,
            ): Formue {
                return tryCreate(id, opprettet, resultat, grunnlag, periode).getOrHandle {
                    throw IllegalArgumentException(it.toString())
                }
            }

            fun tryCreate(
                id: UUID = UUID.randomUUID(),
                opprettet: Tidspunkt,
                resultat: Resultat,
                grunnlag: Formuegrunnlag,
                vurderingsperiode: Periode,
            ): Either<UgyldigVurderingsperiode, Formue> {

                grunnlag.let {
                    if (vurderingsperiode != it.periode) return UgyldigVurderingsperiode.PeriodeForGrunnlagOgVurderingErForskjellig.left()
                }

                return Formue(
                    id = id,
                    opprettet = opprettet,
                    resultat = resultat,
                    grunnlag = grunnlag,
                    periode = vurderingsperiode,
                ).right()
            }

            fun tryCreateFromGrunnlag(
                grunnlag: Formuegrunnlag,
            ): Formue {
                return Formue(
                    id = UUID.randomUUID(),
                    opprettet = grunnlag.opprettet,
                    resultat = if (grunnlag.periode.tilMånedsperioder().all {
                        grunnlag.sumFormue() <= `0,5G`.påDato(it.fraOgMed)
                    }
                    ) Resultat.Innvilget else Resultat.Avslag,
                    grunnlag = grunnlag,
                    periode = grunnlag.periode,
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
