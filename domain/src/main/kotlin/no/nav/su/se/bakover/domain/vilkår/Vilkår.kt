package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.avrund
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.erSortert
import no.nav.su.se.bakover.common.periode.harDuplikater
import no.nav.su.se.bakover.common.periode.harOverlappende
import no.nav.su.se.bakover.common.periode.minAndMaxOf
import no.nav.su.se.bakover.common.periode.minsteAntallSammenhengendePerioder
import no.nav.su.se.bakover.common.periode.minus
import no.nav.su.se.bakover.domain.CopyArgs
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.avslag.Avslagsgrunn
import no.nav.su.se.bakover.domain.grunnlag.FastOppholdINorgeGrunnlag.Companion.equals
import no.nav.su.se.bakover.domain.grunnlag.FlyktningGrunnlag.Companion.equals
import no.nav.su.se.bakover.domain.grunnlag.Formuegrunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag.Bosituasjon.Companion.perioderUtenEPS
import no.nav.su.se.bakover.domain.grunnlag.InstitusjonsoppholdGrunnlag.Companion.equals
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.tidslinje.masker
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
    object Opplysningsplikt : Inngangsvilkår()
}

fun Set<Vilkår>.erLik(other: Set<Vilkår>): Boolean {
    return count() == other.count() &&
        mapIndexed { index, vilkår -> other.toList()[index].erLik(vilkår) }
            .all { it }
}

fun Nel<Vurderingsperiode>.erLik(other: Nel<Vurderingsperiode>): Boolean {
    return count() == other.count() &&
        mapIndexed { index, vurderingsperiode -> other[index].erLik(vurderingsperiode) }
            .all { it }
}

sealed class Vilkårsvurderinger {
    abstract val vilkår: Set<Vilkår>

    abstract val uføre: Vilkår.Uførhet
    abstract val formue: Vilkår.Formue
    abstract val utenlandsopphold: UtenlandsoppholdVilkår
    abstract val opplysningsplikt: OpplysningspliktVilkår
    abstract val erVurdert: Boolean

    fun uføreVilkår(): Vilkår.Uførhet {
        return when (this) {
            is Revurdering -> uføre
            is Søknadsbehandling -> uføre
        }
    }

    fun formueVilkår(): Vilkår.Formue {
        return when (this) {
            is Revurdering -> formue
            is Søknadsbehandling -> formue
        }
    }

    fun utenlandsoppholdVilkår(): UtenlandsoppholdVilkår {
        return when (this) {
            is Revurdering -> utenlandsopphold
            is Søknadsbehandling -> utenlandsopphold
        }
    }

    fun opplysningspliktVilkår(): OpplysningspliktVilkår {
        return when (this) {
            is Revurdering -> opplysningsplikt
            is Søknadsbehandling -> opplysningsplikt
        }
    }

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
        override val formue: Vilkår.Formue,
        val flyktning: FlyktningVilkår = FlyktningVilkår.IkkeVurdert,
        val lovligOpphold: LovligOppholdVilkår = LovligOppholdVilkår.IkkeVurdert,
        val fastOpphold: FastOppholdINorgeVilkår = FastOppholdINorgeVilkår.IkkeVurdert,
        val institusjonsopphold: InstitusjonsoppholdVilkår = InstitusjonsoppholdVilkår.IkkeVurdert,
        override val utenlandsopphold: UtenlandsoppholdVilkår = UtenlandsoppholdVilkår.IkkeVurdert,
        val personligOppmøte: PersonligOppmøteVilkår = PersonligOppmøteVilkår.IkkeVurdert,
        override val opplysningsplikt: OpplysningspliktVilkår = OpplysningspliktVilkår.IkkeVurdert,
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
                    opplysningsplikt,
                )
            }

        override val erVurdert = vilkår.none { it.resultat == Resultat.Uavklart }

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
                opplysningsplikt = opplysningsplikt.lagTidslinje(periode),
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
                is OpplysningspliktVilkår -> copy(opplysningsplikt = vilkår)
            }
        }

        override fun tilVilkårsvurderingerRevurdering(): Revurdering {
            return Revurdering(
                uføre = uføre,
                formue = formue,
                utenlandsopphold = utenlandsopphold,
                opplysningsplikt = opplysningsplikt,
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

        // TODO("flere_satser det gir egentlig ikke mening at vi oppdaterer flere verdier på denne måten, bør sees på/vurderes fjernet")
        fun oppdaterStønadsperiode(
            stønadsperiode: Stønadsperiode,
            formuegrenserFactory: FormuegrenserFactory,
        ): Søknadsbehandling = copy(
            uføre = uføre.oppdaterStønadsperiode(stønadsperiode),
            formue = formue.oppdaterStønadsperiode(stønadsperiode, formuegrenserFactory),
            flyktning = flyktning.oppdaterStønadsperiode(stønadsperiode),
            lovligOpphold = lovligOpphold.oppdaterStønadsperiode(stønadsperiode),
            fastOpphold = fastOpphold.oppdaterStønadsperiode(stønadsperiode),
            institusjonsopphold = institusjonsopphold.oppdaterStønadsperiode(stønadsperiode),
            utenlandsopphold = utenlandsopphold.oppdaterStønadsperiode(stønadsperiode),
            personligOppmøte = personligOppmøte.oppdaterStønadsperiode(stønadsperiode),
        )

        /**
         * Fjerner formue dersom søker ikke har EPS - det finnes et tilsvarende steg i [Grunnlagsdata] for fradrag.
         */
        fun nullstillEpsFormueHvisIngenEps(
            bosituasjon: Grunnlag.Bosituasjon,
        ): Vilkårsvurderinger.Søknadsbehandling {
            return nullstillEpsFormueHvisIngenEps(nonEmptyListOf(bosituasjon))
        }

        /**
         * Fjerner formue dersom søker ikke har EPS - det finnes et tilsvarende steg i [Grunnlagsdata] for fradrag.
         */
        fun nullstillEpsFormueHvisIngenEps(
            bosituasjon: NonEmptyList<Grunnlag.Bosituasjon>,
        ): Vilkårsvurderinger.Søknadsbehandling {
            return this.copy(
                formue = this.formue.fjernEPSFormue(bosituasjon.perioderUtenEPS()),
            )
        }

        companion object {
            fun ikkeVurdert() =
                Søknadsbehandling(
                    uføre = Vilkår.Uførhet.IkkeVurdert,
                    formue = Vilkår.Formue.IkkeVurdert,
                    flyktning = FlyktningVilkår.IkkeVurdert,
                    lovligOpphold = LovligOppholdVilkår.IkkeVurdert,
                    fastOpphold = FastOppholdINorgeVilkår.IkkeVurdert,
                    institusjonsopphold = InstitusjonsoppholdVilkår.IkkeVurdert,
                    utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
                    personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert,
                    opplysningsplikt = OpplysningspliktVilkår.IkkeVurdert,
                )
        }
    }

    data class Revurdering(
        override val uføre: Vilkår.Uførhet,
        override val formue: Vilkår.Formue,
        override val utenlandsopphold: UtenlandsoppholdVilkår,
        override val opplysningsplikt: OpplysningspliktVilkår,
    ) : Vilkårsvurderinger() {

        // TODO jah: Legg til en init her for Vilkår.Revurdering og Vilkår.Søknadsbehandling
        //  slik at vi blant annet kan passe på at periodene enten er null eller like dersom utfylt.
        override val vilkår: Set<Vilkår>
            get() {
                return setOf(
                    uføre,
                    formue,
                    utenlandsopphold,
                    opplysningsplikt,
                )
            }

        override val erVurdert = vilkår.none { it.resultat == Resultat.Uavklart }

        override fun leggTil(vilkår: Vilkår): Revurdering {
            return when (vilkår) {
                is Vilkår.Formue -> copy(formue = vilkår)
                is Vilkår.Uførhet -> copy(uføre = vilkår)
                is UtenlandsoppholdVilkår -> copy(utenlandsopphold = vilkår)
                is OpplysningspliktVilkår -> copy(opplysningsplikt = vilkår)
                is FastOppholdINorgeVilkår,
                is FlyktningVilkår,
                is InstitusjonsoppholdVilkår,
                is LovligOppholdVilkår,
                is PersonligOppmøteVilkår,
                -> {
                    throw IllegalArgumentException("Ukjent vilkår for revurdering: ${vilkår::class}")
                }
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
                opplysningsplikt = opplysningsplikt,
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
                opplysningsplikt = opplysningsplikt.lagTidslinje(periode),
            )
        }

        fun oppdaterStønadsperiode(
            stønadsperiode: Stønadsperiode,
            formuegrenserFactory: FormuegrenserFactory,
        ): Revurdering = copy(
            uføre = uføre.oppdaterStønadsperiode(stønadsperiode),
            formue = formue.oppdaterStønadsperiode(stønadsperiode, formuegrenserFactory),
            utenlandsopphold = utenlandsopphold.oppdaterStønadsperiode(stønadsperiode),
            opplysningsplikt = opplysningsplikt.oppdaterStønadsperiode(stønadsperiode),
        )

        /**
         * Fjerner formue dersom søker ikke har EPS - det finnes et tilsvarende steg i [Grunnlagsdata] for fradrag.
         */
        fun nullstillEpsFormueHvisIngenEps(
            bosituasjon: Grunnlag.Bosituasjon,
        ): Vilkårsvurderinger.Revurdering {
            return nullstillEpsFormueHvisIngenEps(nonEmptyListOf(bosituasjon))
        }

        /**
         * Fjerner formue dersom søker ikke har EPS - det finnes et tilsvarende steg i [Grunnlagsdata] for fradrag.
         */
        fun nullstillEpsFormueHvisIngenEps(
            bosituasjon: NonEmptyList<Grunnlag.Bosituasjon>,
        ): Vilkårsvurderinger.Revurdering {
            return this.copy(
                formue = this.formue.fjernEPSFormue(bosituasjon.perioderUtenEPS()),
            )
        }

        companion object {
            fun ikkeVurdert() = Revurdering(
                uføre = Vilkår.Uførhet.IkkeVurdert,
                formue = Vilkår.Formue.IkkeVurdert,
                utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
                opplysningsplikt = OpplysningspliktVilkår.IkkeVurdert,
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
        val avslagsgrunner = vilkår.map { it.avslagsgrunn() }
        val dato = vilkår.minOf { it.hentTidligesteDatoForAvslag()!! }

        private fun Vilkår.avslagsgrunn(): Avslagsgrunn {
            return when (this) {
                is FastOppholdINorgeVilkår -> {
                    Avslagsgrunn.BOR_OG_OPPHOLDER_SEG_I_NORGE
                }
                is FlyktningVilkår -> {
                    Avslagsgrunn.FLYKTNING
                }
                is Vilkår.Formue -> {
                    Avslagsgrunn.FORMUE
                }
                is InstitusjonsoppholdVilkår -> {
                    Avslagsgrunn.INNLAGT_PÅ_INSTITUSJON
                }
                is LovligOppholdVilkår -> {
                    Avslagsgrunn.OPPHOLDSTILLATELSE
                }
                is OpplysningspliktVilkår -> {
                    Avslagsgrunn.MANGLENDE_DOKUMENTASJON
                }
                is PersonligOppmøteVilkår -> {
                    Avslagsgrunn.PERSONLIG_OPPMØTE
                }
                is Vilkår.Uførhet -> {
                    Avslagsgrunn.UFØRHET
                }
                is UtenlandsoppholdVilkår -> {
                    Avslagsgrunn.UTENLANDSOPPHOLD_OVER_90_DAGER
                }
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

    abstract fun erLik(other: Vilkår): Boolean
    abstract fun lagTidslinje(periode: Periode): Vilkår
    abstract fun slåSammenLikePerioder(): Vilkår

    sealed class Uførhet : Vilkår() {
        override val vilkår = Inngangsvilkår.Uførhet
        abstract val grunnlag: List<Grunnlag.Uføregrunnlag>

        abstract fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Uførhet

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

            override fun slåSammenLikePerioder(): Vilkår {
                return this
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

            override fun slåSammenLikePerioder(): Vurdert {
                return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
            }

            companion object {

                fun tryCreate(
                    vurderingsperioder: Nel<Vurderingsperiode.Uføre>,
                ): Either<UgyldigUførevilkår, Vurdert> {
                    if (vurderingsperioder.harOverlappende()) {
                        return UgyldigUførevilkår.OverlappendeVurderingsperioder.left()
                    }

                    return Vurdert(vurderingsperioder).right()
                }

                fun fromVurderingsperioder(
                    vurderingsperioder: Nel<Vurderingsperiode.Uføre>,
                ): Either<UgyldigUførevilkår, Vurdert> {
                    if (vurderingsperioder.harOverlappende()) {
                        return UgyldigUførevilkår.OverlappendeVurderingsperioder.left()
                    }
                    return Vurdert(vurderingsperioder.kronologisk()).right()
                }
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
                            .minus(vurderingerMedOverlapp.map { it.periode })
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
                                        Stønadsperiode.create(periode = periode),
                                    ),
                                )
                            } else if (senere != null) {
                                acc.add(
                                    periode to senere.oppdaterStønadsperiode(
                                        Stønadsperiode.create(periode = periode),
                                    ),
                                )
                            }
                        }
                        acc
                    }.map {
                        it.second
                    }.let {
                        copy(vurderingsperioder = it.slåSammenLikePerioder())
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
                            ).slåSammenLikePerioder(),
                        )
                    } else {
                        copy(
                            vurderingsperioder = NonEmptyList.fromListUnsafe(
                                listOf(
                                    vurderingsperioder.maxByOrNull { it.periode.tilOgMed }!!
                                        .oppdaterStønadsperiode(stønadsperiode),
                                ),
                            ).slåSammenLikePerioder(),
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

        abstract fun oppdaterStønadsperiode(
            stønadsperiode: Stønadsperiode,
            formuegrenserFactory: FormuegrenserFactory,
        ): Formue

        abstract override fun lagTidslinje(periode: Periode): Formue
        abstract override fun slåSammenLikePerioder(): Formue

        fun harEPSFormue(): Boolean {
            return grunnlag.any { it.harEPSFormue() }
        }

        abstract fun leggTilTomEPSFormueHvisDetMangler(perioder: List<Periode>): Formue

        /**
         * @param perioder vi ønsker å fjerne formue for EPS for. Eventuell formue for EPS som ligger utenfor
         * periodene bevares.
         */
        abstract fun fjernEPSFormue(perioder: List<Periode>): Formue

        object IkkeVurdert : Formue() {
            override val resultat: Resultat = Resultat.Uavklart
            override val erAvslag = false
            override val erInnvilget = false

            override fun hentTidligesteDatoForAvslag(): LocalDate? = null
            override fun erLik(other: Vilkår): Boolean {
                return other is IkkeVurdert
            }

            override fun slåSammenLikePerioder(): IkkeVurdert {
                return this
            }

            override fun leggTilTomEPSFormueHvisDetMangler(perioder: List<Periode>): Formue {
                return this
            }

            override val grunnlag = emptyList<Formuegrunnlag>()

            override fun oppdaterStønadsperiode(
                stønadsperiode: Stønadsperiode,
                formuegrenserFactory: FormuegrenserFactory,
            ): Formue = this

            override fun lagTidslinje(periode: Periode): IkkeVurdert {
                return this
            }

            override fun fjernEPSFormue(perioder: List<Periode>): Formue {
                return this
            }
        }

        data class Vurdert private constructor(
            val vurderingsperioder: Nel<Vurderingsperiode.Formue>,
        ) : Formue() {

            /**
             * Garanterer at disse er sortert og uten duplikater.
             * Merk, i noen tilfeller kan periodene være usammenhengende.
             */
            val perioder = vurderingsperioder.map { it.periode }

            init {
                require(perioder.erSortert())
                require(!perioder.harDuplikater())
                // TODO jah + jacob: Diskuter hvorvidt denne kan være usammenhengende. Bytt denne evt. til en kommentar som forklarer hvorfor
            }

            /** Merk at vi ikke kan garantere at det er hull i perioden */
            val periode: Periode = perioder.minAndMaxOf()

            override fun oppdaterStønadsperiode(
                stønadsperiode: Stønadsperiode,
                formuegrenserFactory: FormuegrenserFactory,
            ): Formue {
                check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn èn vurdering" }
                return this.copy(
                    vurderingsperioder = this.vurderingsperioder.map {
                        it.oppdaterStønadsperiode(stønadsperiode, formuegrenserFactory)
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

            override fun leggTilTomEPSFormueHvisDetMangler(perioder: List<Periode>): Formue {
                return copy(
                    vurderingsperioder = vurderingsperioder.flatMap {
                        it.leggTilTomEPSFormueHvisDetMangler(perioder)
                    },
                )
            }

            override fun fjernEPSFormue(perioder: List<Periode>): Vurdert {
                return copy(vurderingsperioder = vurderingsperioder.flatMap { it.fjernEPSFormue(perioder) })
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

            override fun slåSammenLikePerioder(): Vurdert {
                return copy(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
            }

            override val grunnlag: List<Formuegrunnlag> = vurderingsperioder.map {
                it.grunnlag
            }

            companion object {

                /**
                 * @param grunnlag liste med pairs (måInnhenteMerInformasjon -> formuegrunnlag)
                 */
                fun tryCreateFromGrunnlag(
                    grunnlag: Nel<Pair<Boolean, Formuegrunnlag>>,
                    formuegrenserFactory: FormuegrenserFactory,
                ): Either<UgyldigFormuevilkår, Vurdert> {
                    val vurderingsperioder = grunnlag.map {
                        if (it.first) {
                            Vurderingsperiode.Formue.tryCreateFromGrunnlagMåInnhenteMerInformasjon(
                                grunnlag = it.second,
                            )
                        } else {
                            Vurderingsperiode.Formue.tryCreateFromGrunnlag(
                                grunnlag = it.second,
                                formuegrenserFactory = formuegrenserFactory,
                            )
                        }

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
                    if (vurderingsperioder.harOverlappende()) {
                        return UgyldigFormuevilkår.OverlappendeVurderingsperioder.left()
                    }
                    return Vurdert(vurderingsperioder.kronologisk()).right()
                }
            }

            sealed class UgyldigFormuevilkår {
                object OverlappendeVurderingsperioder : UgyldigFormuevilkår()
            }
        }
    }
}

sealed class Vurderingsperiode {
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
    ) : Vurderingsperiode(), KanPlasseresPåTidslinje<Uføre> {

        fun oppdaterStønadsperiode(stønadsperiode: Stønadsperiode): Uføre {
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
            is CopyArgs.Tidslinje.Maskert -> {
                copy(args.args).copy(opprettet = opprettet.plusUnits(1))
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
            ): Uføre {
                return tryCreate(id, opprettet, resultat, grunnlag, periode).getOrHandle {
                    throw IllegalArgumentException(it.toString())
                }
            }

            fun tryCreate(
                id: UUID = UUID.randomUUID(),
                opprettet: Tidspunkt,
                resultat: Resultat,
                grunnlag: Grunnlag.Uføregrunnlag?,
                vurderingsperiode: Periode,
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
                ).right()
            }
        }

        sealed class UgyldigVurderingsperiode {
            object PeriodeForGrunnlagOgVurderingErForskjellig : UgyldigVurderingsperiode()
        }
    }

    /**
     * En vurderingsperiode for formue er i dag splittet på resultat.
     * Slik at en vurderingsperiode kun kan opprettes med ett resultat (innvilget eller avslag).
     * Klassen støtter å hente uavklart fra databasen (TODO(satsfactory_formue): Sjekk om det finnes tilfeller at dette i preprod/prod-basen.
     * Resultatet er avhengig av formueverdiene i forhold til formuegrensene.
     * Dvs. at formuesummen vurderingsperioden må være mindre enn alle formuegrensene for at man skal få innvilget.
     * Formuegrensene kan variere innenfor perioden.
     * Dersom formuesummen er mellom [minGrenseverdi,maxGrenseverdi] vil vi i teorien ha et delvis avslag som i praksis blir et fullstendig avslag.
     * For en søknadsbehandling er dette korrekt, men for en stønadsendring (revurdering/regulering/etc.) er ikke dette nødvendigvis korrekt.
     */
    data class Formue private constructor(
        override val id: UUID = UUID.randomUUID(),
        override val opprettet: Tidspunkt,
        override val resultat: Resultat,
        override val grunnlag: Formuegrunnlag,
        override val periode: Periode,
    ) : Vurderingsperiode(), KanPlasseresPåTidslinje<Formue> {

        init {
            require(periode == grunnlag.periode) {
                "perioden: $periode og grunnlaget sin periode: ${grunnlag.periode} må være lik."
            }
        }

        /**
         * Kun ment brukt fra søknadsbehandling.
         * Resultatet kan endre seg dersom man treffer en annen formuegrense med lavere verdi og man overskrider denne.
         */
        fun oppdaterStønadsperiode(
            stønadsperiode: Stønadsperiode,
            formuegrenserFactory: FormuegrenserFactory,
        ): Formue {
            // Vi ønsker å regne ut resultatet på nytt, noe ikke copy-funksjonen gjør.
            return tryCreateFromGrunnlag(
                id = this.id,
                grunnlag = this.grunnlag.oppdaterPeriode(stønadsperiode.periode),
                formuegrenserFactory = formuegrenserFactory,
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
            is CopyArgs.Tidslinje.Maskert -> {
                copy(args.args).copy(opprettet = opprettet.plusUnits(1))
            }
        }

        override fun erLik(other: Vurderingsperiode): Boolean {
            return other is Formue &&
                resultat == other.resultat &&
                grunnlag.erLik(other.grunnlag)
        }

        fun harEPSFormue(): Boolean {
            return grunnlag.harEPSFormue()
        }

        fun leggTilTomEPSFormueHvisDetMangler(perioder: List<Periode>): Nel<Formue> {
            val uendret = masker(perioder)
            val endret = leggTilTomEPSFormueHvisDetMangler().masker(
                perioder = uendret.map { it.periode }
                    .minsteAntallSammenhengendePerioder(),
            )
            return Nel.fromListUnsafe(Tidslinje(periode, uendret + endret).tidslinje)
        }

        private fun leggTilTomEPSFormueHvisDetMangler(): Formue {
            return copy(grunnlag = grunnlag.leggTilTomEPSFormueHvisDenMangler())
        }

        /**
         * Fjerner formue for EPS for periodene angitt av [perioder]. Identifiserer først alle periodene hvor det ikke
         * skal skje noen endringer og bevarer verdiene for disse (kan være både med/uten EPS). Deretter fjernes
         * EPS for alle periodene, og alle periodene identifisert i første steg maskeres ut. Syr deretter sammen periodene
         * med/uten endring til en komplett oversikt for [periode].
         */
        fun fjernEPSFormue(perioder: List<Periode>): Nel<Formue> {
            val uendret = masker(perioder = perioder)
            val endret =
                fjernEPSFormue().masker(perioder = uendret.map { it.periode }.minsteAntallSammenhengendePerioder())
            return Nel.fromListUnsafe(Tidslinje(periode, uendret + endret).tidslinje)
        }

        private fun fjernEPSFormue(): Formue {
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
                return tryCreate(
                    id = id,
                    opprettet = opprettet,
                    resultat = resultat,
                    grunnlag = grunnlag,
                    vurderingsperiode = periode,
                ).getOrHandle {
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

            /**
             * I søknadsbehandlingen har vi mulighet til å huke av for at vi må innhente mer informasjon.
             * Saksbehandleren har fremdeles mulighet til å legge inn verdier for søker og eps.
             * Verdiene til søker og EPS  defaultes til 0, bortsett fra hvis søker har fylt inn et kjøretøy, da saksbehandleren fylle ut dette før hen kan lagre 'må innhente mer informasjon' (dette er en 'feature' inntil videre)
             */
            fun tryCreateFromGrunnlagMåInnhenteMerInformasjon(
                id: UUID = UUID.randomUUID(),
                grunnlag: Formuegrunnlag,
            ): Formue {
                return Formue(
                    id = id,
                    opprettet = grunnlag.opprettet,
                    resultat = Resultat.Uavklart,
                    grunnlag = grunnlag,
                    periode = grunnlag.periode,
                )
            }

            /**
             * Brukes av Revurdering og Søknadsbehandling dersom saksbehandler ikke har huka av for at vi skal innhente
             */
            fun tryCreateFromGrunnlag(
                id: UUID = UUID.randomUUID(),
                grunnlag: Formuegrunnlag,
                formuegrenserFactory: FormuegrenserFactory,
            ): Formue {
                return Formue(
                    id = id,
                    opprettet = grunnlag.opprettet,
                    resultat = if (grunnlag.periode.måneder().all {
                            grunnlag.sumFormue() <= formuegrenserFactory.forMåned(it).formuegrense.avrund()
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

fun <T : Vurderingsperiode> Nel<T>.kronologisk(): NonEmptyList<T> {
    return Nel.fromListUnsafe(sortedBy { it.periode })
}

sealed class Resultat {
    object Avslag : Resultat()
    object Innvilget : Resultat()
    object Uavklart : Resultat()
}

fun <T> List<T>.slåSammenLikePerioder(): Nel<T> where T : Vurderingsperiode, T : KanPlasseresPåTidslinje<T> {
    return Nel.fromListUnsafe(
        Tidslinje(
            periode = map { it.periode }.minAndMaxOf(),
            objekter = this,
        ).tidslinje.fold(mutableListOf()) { acc, t ->
            if (acc.isEmpty()) {
                acc.add(t)
            } else if (acc.last().tilstøterOgErLik(t)) {
                val last = acc.removeLast()
                acc.add(
                    last.copy(
                        CopyArgs.Tidslinje.NyPeriode(
                            Periode.create(
                                last.periode.fraOgMed,
                                (t as Vurderingsperiode).periode.tilOgMed,
                            ),
                        ),
                    ),
                )
            } else {
                acc.add(t)
            }
            acc
        },
    )
}

// fun Nel<Vurderingsperiode.Formue>.periode() = this.map { it.periode }.periode()
