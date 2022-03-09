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
import no.nav.su.se.bakover.common.periode.minusListe
import no.nav.su.se.bakover.common.periode.overlappende
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.Grunnbeløp.Companion.`0,5G`
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.grunnlag.FastOppholdINorgeGrunnlag.Companion.equals
import no.nav.su.se.bakover.domain.grunnlag.FlyktningGrunnlag.Companion.equals
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.InstitusjonsoppholdGrunnlag.Companion.equals
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger.Søknadsbehandling.Companion.equals
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFastOppholdINorge.Companion.equals
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeFlyktning.Companion.equals
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeInstitusjonsopphold.Companion.equals
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

/**
Et inngangsvilkår er de vilkårene som kan føre til avslag før det beregnes?
Her har vi utelatt for høy inntekt (SU<0) og su under minstegrense (SU<2%)
 */
sealed class Inngangsvilkår {
    object Uførhet : Inngangsvilkår()
    object Formue : Inngangsvilkår()
    object Flyktning : Inngangsvilkår()
    object LovligOpphold : Inngangsvilkår()
    object Institusjonsopphold : Inngangsvilkår()
    object Utenlandsopphold : Inngangsvilkår()
    object PersonligOppmøte : Inngangsvilkår()
    object FastOppholdINorge : Inngangsvilkår()
}

fun Set<Vilkår>.erLik(other: Set<Vilkår>): Boolean {
    return count() == other.count() &&
        mapIndexed() { index, vilkår -> other.toList()[index].erLik(vilkår) }
            .all { it }
}

fun Nel<Vurderingsperiode>.erLik(other: Nel<Vurderingsperiode>): Boolean {
    return count() == other.count() &&
        mapIndexed() { index, vilkår -> other[index].erLik(vilkår) }
            .all { it }
}

sealed class Vilkårsvurderinger {
    abstract val vilkår: Set<Vilkår>

    abstract val uføre: Vilkår.Uførhet
    abstract val formue: Vilkår.Formue
    abstract val utenlandsopphold: UtenlandsoppholdVilkår

    val periode: Periode?
        get() {
            return vilkår.flatMap { vilkår ->
                when (vilkår) {
                    is FastOppholdINorgeVilkår.Vurdert -> {
                        vilkår.vurderingsperioder.map { it.periode }
                    }
                    is FlyktningVilkår.Vurdert -> {
                        vilkår.vurderingsperioder.map { it.periode }
                    }
                    is Vilkår.Formue.Vurdert -> {
                        vilkår.vurderingsperioder.map { it.periode }
                    }
                    is InstitusjonsoppholdVilkår.Vurdert -> {
                        vilkår.vurderingsperioder.map { it.periode }
                    }
                    is LovligOppholdVilkår.Vurdert -> {
                        vilkår.vurderingsperioder.map { it.periode }
                    }
                    is UtenlandsoppholdVilkår.Vurdert -> {
                        vilkår.vurderingsperioder.map { it.periode }
                    }
                    is PersonligOppmøteVilkår.Vurdert -> {
                        vilkår.vurderingsperioder.map { it.periode }
                    }
                    is Vilkår.Uførhet.Vurdert -> {
                        vilkår.vurderingsperioder.map { it.periode }
                    }
                    else -> emptyList()
                }
            }.ifNotEmpty { this.minAndMaxOf() }
        }

    val resultat: Vilkårsvurderingsresultat
        get() {
            return when {
                vilkår.all { it.resultat is Resultat.Innvilget } -> {
                    Vilkårsvurderingsresultat.Innvilget(vilkår)
                }
                vilkår.any { it.resultat is Resultat.Avslag } -> {
                    Vilkårsvurderingsresultat.Avslag(vilkår.filter { it.resultat is Resultat.Avslag }.toSet())
                }
                else -> {
                    Vilkårsvurderingsresultat.Uavklart(vilkår.filter { it.resultat is Resultat.Uavklart }.toSet())
                }
            }
        }

    abstract fun lagTidslinje(periode: Periode): Vilkårsvurderinger

    abstract fun leggTil(vilkår: Vilkår): Vilkårsvurderinger

    abstract fun tilVilkårsvurderingerRevurdering(): Revurdering
    abstract fun tilVilkårsvurderingerSøknadsbehandling(): Søknadsbehandling

    abstract fun erLik(other: Vilkårsvurderinger): Boolean

    data class Søknadsbehandling(
        override val uføre: Vilkår.Uførhet = Vilkår.Uførhet.IkkeVurdert,
        override val formue: Vilkår.Formue = Vilkår.Formue.IkkeVurdert,
        val flyktning: FlyktningVilkår = FlyktningVilkår.IkkeVurdert,
        val lovligOpphold: LovligOppholdVilkår = LovligOppholdVilkår.IkkeVurdert,
        val fastOpphold: FastOppholdINorgeVilkår = FastOppholdINorgeVilkår.IkkeVurdert,
        val institusjonsopphold: InstitusjonsoppholdVilkår = InstitusjonsoppholdVilkår.IkkeVurdert,
        override val utenlandsopphold: UtenlandsoppholdVilkår = UtenlandsoppholdVilkår.IkkeVurdert,
        val personligOppmøte: PersonligOppmøteVilkår = PersonligOppmøteVilkår.IkkeVurdert,
    ) : Vilkårsvurderinger() {
        override val vilkår: Set<Vilkår>
            get() {
                return setOf(
                    uføre,
                    formue,
                    flyktning,
                    lovligOpphold,
                    fastOpphold,
                    institusjonsopphold,
                    utenlandsopphold,
                    personligOppmøte,
                )
            }

        override fun lagTidslinje(periode: Periode): Søknadsbehandling {
            return copy(
                uføre = uføre.lagTidslinje(periode),
                formue = formue.lagTidslinje(periode),
                flyktning = flyktning.lagTidslinje(periode),
                lovligOpphold = lovligOpphold.lagTidslinje(periode),
                fastOpphold = fastOpphold.lagTidslinje(periode),
                institusjonsopphold = institusjonsopphold.lagTidslinje(periode),
                utenlandsopphold = utenlandsopphold.lagTidslinje(periode),
                personligOppmøte = personligOppmøte.lagTidslinje(periode),
            )
        }

        override fun leggTil(vilkår: Vilkår): Søknadsbehandling {
            return when (vilkår) {
                is FastOppholdINorgeVilkår -> copy(fastOpphold = vilkår)
                is FlyktningVilkår -> copy(flyktning = vilkår)
                is Vilkår.Formue -> copy(formue = vilkår)
                is InstitusjonsoppholdVilkår -> copy(institusjonsopphold = vilkår)
                is LovligOppholdVilkår -> copy(lovligOpphold = vilkår)
                is UtenlandsoppholdVilkår -> copy(utenlandsopphold = vilkår)
                is PersonligOppmøteVilkår -> copy(personligOppmøte = vilkår)
                is Vilkår.Uførhet -> copy(uføre = vilkår)
            }
        }

        override fun tilVilkårsvurderingerRevurdering(): Revurdering {
            return Revurdering(
                uføre = uføre,
                formue = formue,
                utenlandsopphold = utenlandsopphold,
            )
        }

        override fun tilVilkårsvurderingerSøknadsbehandling(): Søknadsbehandling {
            return this
        }

        override fun erLik(other: Vilkårsvurderinger): Boolean {
            return other is Søknadsbehandling && vilkår.erLik(other.vilkår)
        }

        /**
         * Override av [equals] for å slippe å endre alle eksisterende tester som baserer seg på objektliket.
         * Må modifiserers etterhvert som disse dataene begynner å lagres.
         */
        override fun equals(other: Any?): Boolean {
            return other is Søknadsbehandling && erLik(other)
        }

        /**
         *  Bro mellom [Behandlingsinformasjon] og [Vilkårsvurderinger]. Mapper over tilgjengelig data til et format
         *  som vilkårsvurderingene forstår. På denne måten kan [Vilkårsvurderinger] eie konseptet vurdering av vilkår
         *  og ikke [Behandlingsinformasjon]. For vilkår/grunnlag som fullt og helt er konvertert til aktuell modell,
         *  trengs det ingen mapping, da disse kommer inn fra andre steder enn [Behandlingsinformasjon] og vil være
         *  tilgjengelig på korrekt format.
         */
        fun oppdater(
            stønadsperiode: Stønadsperiode,
            behandlingsinformasjon: Behandlingsinformasjon,
            grunnlagsdata: Grunnlagsdata, // For validering av formue
            clock: Clock,
        ): Søknadsbehandling {
            return behandlingsinformasjon.vilkår.mapNotNull {
                when (it) {
                    is Behandlingsinformasjon.Flyktning -> {
                        it.tilVilkår(stønadsperiode, clock)
                    }
                    is Behandlingsinformasjon.LovligOpphold -> {
                        it.tilVilkår(stønadsperiode, clock)
                    }
                    is Behandlingsinformasjon.FastOppholdINorge -> {
                        it.tilVilkår(stønadsperiode, clock)
                    }
                    is Behandlingsinformasjon.Institusjonsopphold -> {
                        it.tilVilkår(stønadsperiode, clock)
                    }
                    is Behandlingsinformasjon.Formue -> {
                        it.tilVilkår(stønadsperiode, grunnlagsdata.bosituasjon, clock)
                    }
                    is Behandlingsinformasjon.PersonligOppmøte -> {
                        it.tilVilkår(stønadsperiode, clock)
                    }
                    null -> {
                        null // elementer kan være null før de er vurdert
                    }
                    else -> {
                        throw IllegalArgumentException("Ukjent type: ${it::class} for mapping mellom ${Behandlingsinformasjon::class} og ${Vilkårsvurderinger::class}")
                    }
                }
            }.fold(this) { acc, vilkår -> acc.leggTil(vilkår) }
        }

        fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Søknadsbehandling = copy(
            uføre = uføre.oppdaterStønadsperiode(stønadsperiode),
            formue = formue.oppdaterStønadsperiode(stønadsperiode),
            flyktning = flyktning.oppdaterStønadsperiode(stønadsperiode),
            lovligOpphold = lovligOpphold.oppdaterStønadsperiode(stønadsperiode),
            fastOpphold = fastOpphold.oppdaterStønadsperiode(stønadsperiode),
            institusjonsopphold = institusjonsopphold.oppdaterStønadsperiode(stønadsperiode),
            utenlandsopphold = utenlandsopphold.oppdaterStønadsperiode(stønadsperiode),
            personligOppmøte = personligOppmøte.oppdaterStønadsperiode(stønadsperiode),
        )

        companion object {
            val IkkeVurdert = Søknadsbehandling(
                uføre = Vilkår.Uførhet.IkkeVurdert,
                formue = Vilkår.Formue.IkkeVurdert,
                flyktning = FlyktningVilkår.IkkeVurdert,
                lovligOpphold = LovligOppholdVilkår.IkkeVurdert,
                fastOpphold = FastOppholdINorgeVilkår.IkkeVurdert,
                institusjonsopphold = InstitusjonsoppholdVilkår.IkkeVurdert,
                utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
                personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert,
            )
        }
    }

    data class Revurdering(
        override val uføre: Vilkår.Uførhet,
        override val formue: Vilkår.Formue,
        override val utenlandsopphold: UtenlandsoppholdVilkår,
    ) : Vilkårsvurderinger() {
        override val vilkår: Set<Vilkår>
            get() {
                return setOf(
                    uføre,
                    formue,
                    utenlandsopphold,
                )
            }

        override fun leggTil(vilkår: Vilkår): Revurdering {
            return when (vilkår) {
                is Vilkår.Formue -> copy(formue = vilkår)
                is Vilkår.Uførhet -> copy(uføre = vilkår)
                is UtenlandsoppholdVilkår -> copy(utenlandsopphold = vilkår)
                else -> throw IllegalArgumentException("Ukjent vilkår for revurdering: ${vilkår::class}")
            }
        }

        override fun tilVilkårsvurderingerRevurdering(): Revurdering {
            return this
        }

        override fun tilVilkårsvurderingerSøknadsbehandling(): Søknadsbehandling {
            return Søknadsbehandling(
                uføre = uføre,
                formue = formue,
                utenlandsopphold = utenlandsopphold,
            )
        }

        override fun erLik(other: Vilkårsvurderinger): Boolean {
            return other is Revurdering && vilkår.erLik(other.vilkår)
        }

        /**
         * Override av [equals] for å slippe å endre alle eksisterende tester som baserer seg på objektliket.
         * Må modifiserers etterhvert som disse dataene begynner å lagres.
         */
        override fun equals(other: Any?): Boolean {
            return other is Revurdering && erLik(other)
        }

        override fun lagTidslinje(periode: Periode): Revurdering {
            return copy(
                uføre = uføre.lagTidslinje(periode),
                formue = formue.lagTidslinje(periode),
                utenlandsopphold = utenlandsopphold.lagTidslinje(periode),
            )
        }

        fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Revurdering = copy(
            uføre = uføre.oppdaterStønadsperiode(stønadsperiode),
            formue = formue.oppdaterStønadsperiode(stønadsperiode),
            utenlandsopphold = utenlandsopphold.oppdaterStønadsperiode(stønadsperiode),
        )

        companion object {
            val IkkeVurdert = Revurdering(
                uføre = Vilkår.Uførhet.IkkeVurdert,
                formue = Vilkår.Formue.IkkeVurdert,
                utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
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
    ) : Vilkårsvurderingsresultat() {
        val avslagsgrunner = vilkår.map { it.vilkår.tilAvslagsgrunn() }
        val dato = vilkår.minOf { it.hentTidligesteDatoForAvslag()!! }

        private fun Inngangsvilkår.tilAvslagsgrunn(): Avslagsgrunn {
            return when (this) {
                Inngangsvilkår.FastOppholdINorge -> Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE
                Inngangsvilkår.Flyktning -> Avslagsgrunn.FLYKTNING
                Inngangsvilkår.Formue -> Avslagsgrunn.FORMUE
                Inngangsvilkår.Institusjonsopphold -> Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON
                Inngangsvilkår.LovligOpphold -> Avslagsgrunn.OPPHOLDSTILLATELSE
                Inngangsvilkår.Utenlandsopphold -> Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER
                Inngangsvilkår.PersonligOppmøte -> Avslagsgrunn.PERSONLIG_OPPMØTE
                Inngangsvilkår.Uførhet -> Avslagsgrunn.UFØRHET
            }
        }

        fun erNøyaktigÅrsak(inngangsvilkår: Inngangsvilkår): Boolean {
            return vilkår.singleOrNull { it.vilkår == inngangsvilkår }?.let { true }
                ?: if (vilkår.size == 1) false else throw IllegalStateException("Opphør av flere vilkår er ikke støttet, opphørte vilkår:$vilkår")
        }
    }

    data class Innvilget(
        val vilkår: Set<Vilkår>,
    ) : Vilkårsvurderingsresultat()

    data class Uavklart(
        val vilkår: Set<Vilkår>,
    ) : Vilkårsvurderingsresultat()
}

/**
 * Vurderingen av et vilkår mot en eller flere grunnlagsdata
 */
sealed class Vilkår {
    abstract val resultat: Resultat
    abstract val erAvslag: Boolean
    abstract val erInnvilget: Boolean
    abstract val vilkår: Inngangsvilkår

    abstract fun hentTidligesteDatoForAvslag(): LocalDate?
    abstract fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Vilkår
    abstract fun erLik(other: Vilkår): Boolean
    abstract fun lagTidslinje(periode: Periode): Vilkår

    sealed class Uførhet : Vilkår() {
        override val vilkår = Inngangsvilkår.Uførhet
        abstract val grunnlag: List<Grunnlag.Uføregrunnlag>

        abstract override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Uførhet

        abstract override fun lagTidslinje(periode: Periode): Uførhet

        object IkkeVurdert : Uførhet() {
            override val resultat: Resultat = Resultat.Uavklart
            override val erAvslag = false
            override val erInnvilget = false
            override val grunnlag = emptyList<Grunnlag.Uføregrunnlag>()

            override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): IkkeVurdert = this

            override fun lagTidslinje(periode: Periode): IkkeVurdert {
                return this
            }

            override fun hentTidligesteDatoForAvslag(): LocalDate? = null

            override fun erLik(other: Vilkår): Boolean {
                return other is IkkeVurdert
            }
        }

        data class Vurdert private constructor(
            val vurderingsperioder: Nel<Vurderingsperiode.Uføre>,
        ) : Uførhet() {
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

            override fun erLik(other: Vilkår): Boolean {
                return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
            }

            fun slåSammenVurderingsperioder(): Either<UgyldigUførevilkår, Vurdert> {
                return fromVurderingsperioder(vurderingsperioder = vurderingsperioder.slåSammenVurderingsperiode())
            }

            companion object {

                fun tryCreate(
                    vurderingsperioder: Nel<Vurderingsperiode.Uføre>,
                ): Either<UgyldigUførevilkår, Vurdert> {
                    if (vurderingsperioder.overlappende()) {
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

                fun fromVurderingsperioder(
                    vurderingsperioder: Nel<Vurderingsperiode.Uføre>,
                ): Either<UgyldigUførevilkår, Vurdert> {
                    if (vurderingsperioder.overlappende()) {
                        return UgyldigUførevilkår.OverlappendeVurderingsperioder.left()
                    }
                    return Vurdert(vurderingsperioder).right()
                }

                private fun List<Vurderingsperiode.Uføre>.sisteUføreperiodeErLikOgTilstøtende(other: Vurderingsperiode.Uføre) =
                    this.last().tilstøterOgErLik(other)
            }

            sealed class UgyldigUførevilkår {
                object OverlappendeVurderingsperioder : UgyldigUførevilkår()
            }

            override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Vurdert {
                val overlapp = vurderingsperioder.any { it.periode overlapper stønadsperiode.periode }

                return if (overlapp) {
                    val vurderingerMedOverlapp = lagTidslinje(stønadsperiode.periode).vurderingsperioder
                    val manglendePerioder =
                        listOf(stønadsperiode.periode)
                            .minusListe(vurderingerMedOverlapp.map { it.periode })
                            .sortedBy { it.fraOgMed }.toSet()
                    val paired: List<Pair<Periode, Vurderingsperiode.Uføre?>> = vurderingerMedOverlapp.map {
                        it.periode to it
                    }.plus(
                        manglendePerioder.map {
                            it to null
                        },
                    )

                    paired.fold(mutableListOf<Pair<Periode, Vurderingsperiode.Uføre>>()) { acc, (periode, vurdering) ->
                        if (vurdering != null) {
                            acc.add((periode to vurdering))
                        } else {
                            val tidligere =
                                vurderingerMedOverlapp.lastOrNull { it.periode starterSamtidigEllerTidligere periode }
                            val senere =
                                vurderingerMedOverlapp.firstOrNull { it.periode starterSamtidigEllerSenere periode }

                            if (tidligere != null) {
                                acc.add(
                                    periode to tidligere.oppdaterStønadsperiode(
                                        Stønadsperiode.create(
                                            periode = periode,
                                            begrunnelse = stønadsperiode.begrunnelse,
                                        ),
                                    ),
                                )
                            } else if (senere != null) {
                                acc.add(
                                    periode to senere.oppdaterStønadsperiode(
                                        Stønadsperiode.create(
                                            periode = periode,
                                            begrunnelse = stønadsperiode.begrunnelse,
                                        ),
                                    ),
                                )
                            }
                        }
                        acc
                    }.map {
                        it.second
                    }.let {
                        copy(
                            vurderingsperioder = NonEmptyList.fromListUnsafe(it).slåSammenVurderingsperiode(),
                        )
                    }
                } else {
                    val tidligere = stønadsperiode.periode.starterTidligere(
                        vurderingsperioder.map { it.periode }
                            .minByOrNull { it.fraOgMed }!!,
                    )

                    if (tidligere) {
                        copy(
                            vurderingsperioder = NonEmptyList.fromListUnsafe(
                                listOf(
                                    vurderingsperioder.minByOrNull { it.periode.fraOgMed }!!
                                        .oppdaterStønadsperiode(stønadsperiode),
                                ),
                            ).slåSammenVurderingsperiode(),
                        )
                    } else {
                        copy(
                            vurderingsperioder = NonEmptyList.fromListUnsafe(
                                listOf(
                                    vurderingsperioder.maxByOrNull { it.periode.tilOgMed }!!
                                        .oppdaterStønadsperiode(stønadsperiode),
                                ),
                            ).slåSammenVurderingsperiode(),
                        )
                    }
                }
            }

            override fun lagTidslinje(periode: Periode): Vurdert {
                return copy(
                    vurderingsperioder = Nel.fromListUnsafe(
                        Tidslinje(
                            periode = periode,
                            objekter = vurderingsperioder,
                        ).tidslinje,
                    ),
                )
            }
        }
    }

    sealed class Formue : Vilkår() {
        override val vilkår = Inngangsvilkår.Formue
        abstract val grunnlag: List<Formuegrunnlag>

        abstract override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Formue

        abstract override fun lagTidslinje(periode: Periode): Formue

        fun harEPSFormue(): Boolean {
            return grunnlag.any { it.harEPSFormue() }
        }

        abstract fun fjernEPSFormue(): Formue

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
            override fun erLik(other: Vilkår): Boolean {
                return other is IkkeVurdert
            }

            override val grunnlag = emptyList<Formuegrunnlag>()

            override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Formue = this

            override fun lagTidslinje(periode: Periode): IkkeVurdert {
                return this
            }

            override fun fjernEPSFormue(): Formue {
                return this
            }
        }

        data class Vurdert private constructor(
            val vurderingsperioder: Nel<Vurderingsperiode.Formue>,
        ) : Formue() {
            override fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Formue {
                check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn èn vurdering" }
                return this.copy(
                    vurderingsperioder = this.vurderingsperioder.map {
                        it.oppdaterStønadsperiode(stønadsperiode)
                    },
                )
            }

            override fun lagTidslinje(periode: Periode): Vurdert {
                return copy(
                    vurderingsperioder = Nel.fromListUnsafe(
                        Tidslinje(
                            periode = periode,
                            objekter = vurderingsperioder,
                        ).tidslinje,
                    ),
                )
            }

            override fun fjernEPSFormue(): Formue {
                return copy(vurderingsperioder = vurderingsperioder.map { it.fjernEPSFormue() })
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

            override fun erLik(other: Vilkår): Boolean {
                return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
            }

            override val grunnlag: List<Formuegrunnlag> = vurderingsperioder.map {
                it.grunnlag
            }

            fun slåSammenVurderingsperioder(): Either<UgyldigFormuevilkår, Vurdert> {
                return fromVurderingsperioder(vurderingsperioder = vurderingsperioder.slåSammenVurderingsperioder())
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

                fun fromVurderingsperioder(
                    vurderingsperioder: Nel<Vurderingsperiode.Formue>,
                ): Either<UgyldigFormuevilkår, Vurdert> {
                    if (vurderingsperioder.overlappende()) {
                        return UgyldigFormuevilkår.OverlappendeVurderingsperioder.left()
                    }
                    return Vurdert(vurderingsperioder).right()
                }

                fun Nel<Vurderingsperiode.Formue>.slåSammenVurderingsperioder(): Nel<Vurderingsperiode.Formue> {
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
                    this.last().tilstøterOgErLik(other)
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
            return other is Uføre &&
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
            return other is Formue &&
                resultat == other.resultat &&
                grunnlag.erLik(other.grunnlag)
        }

        fun fjernEPSFormue(): Formue {
            return copy(grunnlag = grunnlag.fjernEPSFormue())
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

fun Periode.inneholderAlle(vurderingsperioder: NonEmptyList<Vurderingsperiode>): Boolean {
    return vurderingsperioder.all { this inneholder it.periode }
}

sealed class Resultat {
    object Avslag : Resultat()
    object Innvilget : Resultat()
    object Uavklart : Resultat()
}
