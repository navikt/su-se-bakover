package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.Nel
import arrow.core.NonEmptyList
import arrow.core.getOrHandle
import arrow.core.left
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
import no.nav.su.se.bakover.domain.grunnlag.InstitusjonsoppholdGrunnlag.Companion.equals
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.tidslinje.KanPlasseresPåTidslinje
import no.nav.su.se.bakover.domain.tidslinje.Tidslinje
import no.nav.su.se.bakover.domain.tidslinje.masker
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger.Søknadsbehandling.Uføre.Companion.equals
import no.nav.su.se.bakover.domain.vilkår.Vurderingsperiode.Formue
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
    object Familiegjenforening : Inngangsvilkår()
    object Pensjon : Inngangsvilkår()
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

    abstract val lovligOpphold: LovligOppholdVilkår
    abstract val formue: Vilkår.Formue
    abstract val utenlandsopphold: UtenlandsoppholdVilkår
    abstract val opplysningsplikt: OpplysningspliktVilkår
    val erVurdert: Boolean by lazy { vilkår.none { it.resultat is Resultat.Uavklart } }

    protected fun kastHvisPerioderErUlike() {
        // Merk at hvert enkelt [Vilkår] passer på sine egne data (som f.eks. at periodene er sorterte og uten duplikater)
        vilkår.map { it.perioder }.zipWithNext { a, b ->
            // Vilkår med tomme perioder har ikke blitt vurdert enda.
            if (a.isNotEmpty() && b.isNotEmpty()) {
                require(a == b) {
                    "Periodene til Vilkårsvurderinger er ulike. $a vs $b."
                }
            }
        }
    }

    fun uføreVilkår(): Either<VilkårEksistererIkke, Vilkår.Uførhet> {
        return when (this) {
            is Revurdering.Uføre -> uføre.right()
            is Søknadsbehandling.Uføre -> uføre.right()
            is Revurdering.Alder -> VilkårEksistererIkke.left()
            is Søknadsbehandling.Alder -> VilkårEksistererIkke.left()
        }
    }

    fun flyktningVilkår(): Either<VilkårEksistererIkke, FlyktningVilkår> {
        return when (this) {
            is Revurdering.Alder -> VilkårEksistererIkke.left()
            is Revurdering.Uføre -> VilkårEksistererIkke.left()
            is Søknadsbehandling.Alder -> VilkårEksistererIkke.left()
            is Søknadsbehandling.Uføre -> flyktning.right()
        }
    }

    fun familiegjenforening() = when (this) {
        is Revurdering.Alder -> familiegjenforening.right()
        is Revurdering.Uføre -> VilkårEksistererIkke.left()
        is Søknadsbehandling.Alder -> familiegjenforening.right()
        is Søknadsbehandling.Uføre -> VilkårEksistererIkke.left()
    }

    object VilkårEksistererIkke

    fun lovligOppholdVilkår() = lovligOpphold

    fun formueVilkår(): Vilkår.Formue {
        return formue
    }

    fun utenlandsoppholdVilkår(): UtenlandsoppholdVilkår {
        return utenlandsopphold
    }

    fun opplysningspliktVilkår(): OpplysningspliktVilkår {
        return opplysningsplikt
    }

    fun pensjonsVilkår(): Either<VilkårEksistererIkke, PensjonsVilkår> {
        return when (this) {
            is Revurdering.Alder -> pensjon.right()
            is Revurdering.Uføre -> VilkårEksistererIkke.left()
            is Søknadsbehandling.Alder -> pensjon.right()
            is Søknadsbehandling.Uføre -> VilkårEksistererIkke.left()
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
                    is OpplysningspliktVilkår.Vurdert -> {
                        vilkår.vurderingsperioder.map { it.periode }
                    }
                    is PensjonsVilkår.Vurdert -> {
                        vilkår.vurderingsperioder.map { it.periode }
                    }
                    is FamiliegjenforeningVilkår.Vurdert -> {
                        vilkår.vurderingsperioder.map { it.periode }
                    }
                    FastOppholdINorgeVilkår.IkkeVurdert,
                    FlyktningVilkår.IkkeVurdert,
                    Vilkår.Formue.IkkeVurdert,
                    LovligOppholdVilkår.IkkeVurdert,
                    InstitusjonsoppholdVilkår.IkkeVurdert,
                    OpplysningspliktVilkår.IkkeVurdert,
                    PersonligOppmøteVilkår.IkkeVurdert,
                    Vilkår.Uførhet.IkkeVurdert,
                    UtenlandsoppholdVilkår.IkkeVurdert,
                    PensjonsVilkår.IkkeVurdert,
                    FamiliegjenforeningVilkår.IkkeVurdert,
                    -> emptyList()
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

    sealed class Søknadsbehandling : Vilkårsvurderinger() {
        abstract override val formue: Vilkår.Formue
        abstract override val lovligOpphold: LovligOppholdVilkår
        abstract val fastOpphold: FastOppholdINorgeVilkår
        abstract val institusjonsopphold: InstitusjonsoppholdVilkår
        abstract override val utenlandsopphold: UtenlandsoppholdVilkår
        abstract val personligOppmøte: PersonligOppmøteVilkår
        abstract override val opplysningsplikt: OpplysningspliktVilkår

        abstract override fun leggTil(vilkår: Vilkår): Søknadsbehandling
        abstract fun oppdater(
            stønadsperiode: Stønadsperiode,
            behandlingsinformasjon: Behandlingsinformasjon,
            clock: Clock,
        ): Søknadsbehandling

        abstract fun oppdaterStønadsperiode(
            stønadsperiode: Stønadsperiode,
            formuegrenserFactory: FormuegrenserFactory,
        ): Søknadsbehandling

        data class Uføre(
            override val formue: Vilkår.Formue,
            override val lovligOpphold: LovligOppholdVilkår,
            override val fastOpphold: FastOppholdINorgeVilkår,
            override val institusjonsopphold: InstitusjonsoppholdVilkår,
            override val utenlandsopphold: UtenlandsoppholdVilkår,
            override val personligOppmøte: PersonligOppmøteVilkår,
            override val opplysningsplikt: OpplysningspliktVilkår,
            val uføre: Vilkår.Uførhet,
            val flyktning: FlyktningVilkår,
        ) : Søknadsbehandling() {
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

            init {
                kastHvisPerioderErUlike()
            }

            override fun lagTidslinje(periode: Periode): Uføre {
                return Uføre(
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

            override fun leggTil(vilkår: Vilkår): Uføre {
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
                    is FamiliegjenforeningVilkår -> throw IllegalArgumentException("Kan ikke legge til FamiliegjenforeningVilkår for vilkårsvurdering uføre")
                    is PensjonsVilkår -> {
                        throw IllegalArgumentException("Kan ikke legge til ${vilkår::class} for ${this::class}")
                    }
                }
            }

            override fun tilVilkårsvurderingerRevurdering(): Revurdering.Uføre {
                return Revurdering.Uføre(
                    uføre = uføre,
                    lovligOpphold = lovligOpphold,
                    formue = formue,
                    utenlandsopphold = utenlandsopphold,
                    opplysningsplikt = opplysningsplikt,
                )
            }

            override fun tilVilkårsvurderingerSøknadsbehandling(): Uføre {
                return this
            }

            override fun erLik(other: Vilkårsvurderinger): Boolean {
                return other is Uføre && vilkår.erLik(other.vilkår)
            }

            /**
             * Override av [equals] for å slippe å endre alle eksisterende tester som baserer seg på objektliket.
             * Må modifiserers etterhvert som disse dataene begynner å lagres.
             */
            override fun equals(other: Any?): Boolean {
                return other is Uføre && erLik(other)
            }

            /**
             *  Bro mellom [Behandlingsinformasjon] og [Vilkårsvurderinger]. Mapper over tilgjengelig data til et format
             *  som vilkårsvurderingene forstår. På denne måten kan [Vilkårsvurderinger] eie konseptet vurdering av vilkår
             *  og ikke [Behandlingsinformasjon]. For vilkår/grunnlag som fullt og helt er konvertert til aktuell modell,
             *  trengs det ingen mapping, da disse kommer inn fra andre steder enn [Behandlingsinformasjon] og vil være
             *  tilgjengelig på korrekt format.
             */
            override fun oppdater(
                stønadsperiode: Stønadsperiode,
                behandlingsinformasjon: Behandlingsinformasjon,
                clock: Clock,
            ): Uføre {
                return behandlingsinformasjon.vilkår.mapNotNull {
                    when (it) {
                        is Behandlingsinformasjon.Flyktning -> {
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
            override fun oppdaterStønadsperiode(
                stønadsperiode: Stønadsperiode,
                formuegrenserFactory: FormuegrenserFactory,
            ): Uføre = Uføre(
                uføre = uføre.oppdaterStønadsperiode(stønadsperiode),
                formue = formue.oppdaterStønadsperiode(stønadsperiode, formuegrenserFactory),
                flyktning = flyktning.oppdaterStønadsperiode(stønadsperiode),
                lovligOpphold = lovligOpphold.oppdaterStønadsperiode(stønadsperiode),
                fastOpphold = fastOpphold.oppdaterStønadsperiode(stønadsperiode),
                institusjonsopphold = institusjonsopphold.oppdaterStønadsperiode(stønadsperiode),
                utenlandsopphold = utenlandsopphold.oppdaterStønadsperiode(stønadsperiode),
                personligOppmøte = personligOppmøte.oppdaterStønadsperiode(stønadsperiode),
                opplysningsplikt = opplysningsplikt.oppdaterStønadsperiode(stønadsperiode),
            )

            companion object {
                fun ikkeVurdert() = Uføre(
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

        data class Alder(
            override val formue: Vilkår.Formue,
            override val lovligOpphold: LovligOppholdVilkår,
            override val fastOpphold: FastOppholdINorgeVilkår,
            override val institusjonsopphold: InstitusjonsoppholdVilkår,
            override val utenlandsopphold: UtenlandsoppholdVilkår,
            override val personligOppmøte: PersonligOppmøteVilkår,
            override val opplysningsplikt: OpplysningspliktVilkår,
            val pensjon: PensjonsVilkår = PensjonsVilkår.IkkeVurdert,
            val familiegjenforening: FamiliegjenforeningVilkår,
        ) : Søknadsbehandling() {
            override val vilkår: Set<Vilkår> = setOf(
                formue,
                lovligOpphold,
                fastOpphold,
                institusjonsopphold,
                utenlandsopphold,
                personligOppmøte,
                opplysningsplikt,
                pensjon,
                familiegjenforening,
            )

            init {
                kastHvisPerioderErUlike()
            }

            override fun lagTidslinje(periode: Periode): Søknadsbehandling {
                return Alder(
                    formue = formue.lagTidslinje(periode),
                    lovligOpphold = lovligOpphold.lagTidslinje(periode),
                    fastOpphold = fastOpphold.lagTidslinje(periode),
                    institusjonsopphold = institusjonsopphold.lagTidslinje(periode),
                    utenlandsopphold = utenlandsopphold.lagTidslinje(periode),
                    personligOppmøte = personligOppmøte.lagTidslinje(periode),
                    opplysningsplikt = opplysningsplikt.lagTidslinje(periode),
                    pensjon = pensjon.lagTidslinje(periode),
                    familiegjenforening = familiegjenforening.lagTidslinje(periode),
                )
            }

            override fun leggTil(vilkår: Vilkår): Alder {
                return when (vilkår) {
                    is FastOppholdINorgeVilkår -> copy(fastOpphold = vilkår)
                    is FlyktningVilkår -> {
                        throw IllegalArgumentException("Kan ikke legge til flyktningvilkår for vilkårsvurdering alder")
                    }
                    is Vilkår.Formue -> copy(formue = vilkår)
                    is InstitusjonsoppholdVilkår -> copy(institusjonsopphold = vilkår)
                    is LovligOppholdVilkår -> copy(lovligOpphold = vilkår)
                    is OpplysningspliktVilkår -> copy(opplysningsplikt = vilkår)
                    is PersonligOppmøteVilkår -> copy(personligOppmøte = vilkår)
                    is UtenlandsoppholdVilkår -> copy(utenlandsopphold = vilkår)
                    is Vilkår.Uførhet -> {
                        throw IllegalArgumentException("Kan ikke legge til uførevilkår for vilkårsvurdering alder")
                    }
                    is FamiliegjenforeningVilkår -> copy(familiegjenforening = vilkår)
                    is PensjonsVilkår -> copy(pensjon = vilkår)
                }
            }

            override fun oppdater(
                stønadsperiode: Stønadsperiode,
                behandlingsinformasjon: Behandlingsinformasjon,
                clock: Clock,
            ): Alder {
                return behandlingsinformasjon.vilkår.mapNotNull {
                    when (it) {
                        is Behandlingsinformasjon.Flyktning -> {
                            null // TODO("vilkårsvurdering_alder tålererer dette inntil vi har fått denne ut av behandlingsinformasjon")
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

            override fun oppdaterStønadsperiode(
                stønadsperiode: Stønadsperiode,
                formuegrenserFactory: FormuegrenserFactory,
            ): Alder = Alder(
                formue = formue.oppdaterStønadsperiode(stønadsperiode, formuegrenserFactory),
                lovligOpphold = lovligOpphold.oppdaterStønadsperiode(stønadsperiode),
                fastOpphold = fastOpphold.oppdaterStønadsperiode(stønadsperiode),
                institusjonsopphold = institusjonsopphold.oppdaterStønadsperiode(stønadsperiode),
                utenlandsopphold = utenlandsopphold.oppdaterStønadsperiode(stønadsperiode),
                personligOppmøte = personligOppmøte.oppdaterStønadsperiode(stønadsperiode),
                opplysningsplikt = opplysningsplikt.oppdaterStønadsperiode(stønadsperiode),
                pensjon = pensjon.oppdaterStønadsperiode(stønadsperiode),
                familiegjenforening = familiegjenforening.oppdaterStønadsperiode(stønadsperiode)
            )

            override fun tilVilkårsvurderingerRevurdering(): Revurdering.Alder {
                return Revurdering.Alder(
                    lovligOpphold = lovligOpphold,
                    formue = formue,
                    utenlandsopphold = utenlandsopphold,
                    opplysningsplikt = opplysningsplikt,
                    pensjon = pensjon,
                    familiegjenforening = familiegjenforening,
                )
            }

            override fun tilVilkårsvurderingerSøknadsbehandling(): Alder {
                return this
            }

            override fun erLik(other: Vilkårsvurderinger): Boolean {
                return other is Alder && vilkår.erLik(other.vilkår)
            }

            companion object {
                fun ikkeVurdert() = Alder(
                    formue = Vilkår.Formue.IkkeVurdert,
                    lovligOpphold = LovligOppholdVilkår.IkkeVurdert,
                    fastOpphold = FastOppholdINorgeVilkår.IkkeVurdert,
                    institusjonsopphold = InstitusjonsoppholdVilkår.IkkeVurdert,
                    utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
                    personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert,
                    opplysningsplikt = OpplysningspliktVilkår.IkkeVurdert,
                    pensjon = PensjonsVilkår.IkkeVurdert,
                    familiegjenforening = FamiliegjenforeningVilkår.IkkeVurdert,
                )
            }
        }
    }

    sealed class Revurdering : Vilkårsvurderinger() {
        abstract override val lovligOpphold: LovligOppholdVilkår
        abstract override val formue: Vilkår.Formue
        abstract override val utenlandsopphold: UtenlandsoppholdVilkår
        abstract override val opplysningsplikt: OpplysningspliktVilkår

        abstract override fun leggTil(vilkår: Vilkår): Revurdering

        data class Uføre(
            val uføre: Vilkår.Uførhet,
            override val lovligOpphold: LovligOppholdVilkår,
            override val formue: Vilkår.Formue,
            override val utenlandsopphold: UtenlandsoppholdVilkår,
            override val opplysningsplikt: OpplysningspliktVilkår,
        ) : Revurdering() {

            override val vilkår: Set<Vilkår> = setOf(
                uføre,
                formue,
                utenlandsopphold,
                opplysningsplikt,
            )

            init {
                kastHvisPerioderErUlike()
            }

            override fun leggTil(vilkår: Vilkår): Uføre {
                return when (vilkår) {
                    is Vilkår.Formue -> copy(formue = vilkår)
                    is Vilkår.Uførhet -> copy(uføre = vilkår)
                    is UtenlandsoppholdVilkår -> copy(utenlandsopphold = vilkår)
                    is OpplysningspliktVilkår -> copy(opplysningsplikt = vilkår)
                    is LovligOppholdVilkår -> copy(lovligOpphold = vilkår)
                    is FastOppholdINorgeVilkår,
                    is FlyktningVilkår,
                    is InstitusjonsoppholdVilkår,
                    is PersonligOppmøteVilkår,
                    -> {
                        throw IllegalArgumentException("Ukjent vilkår for revurdering av uføre: ${vilkår::class}")
                    }
                    is FamiliegjenforeningVilkår,
                    is PensjonsVilkår,
                    -> {
                        throw IllegalArgumentException("Kan ikke legge til ${vilkår::class} for ${this::class}")
                    }
                }
            }

            override fun tilVilkårsvurderingerRevurdering(): Revurdering {
                return this
            }

            override fun tilVilkårsvurderingerSøknadsbehandling(): Søknadsbehandling {
                return Søknadsbehandling.Uføre(
                    uføre = uføre,
                    formue = formue,
                    utenlandsopphold = utenlandsopphold,
                    opplysningsplikt = opplysningsplikt,
                    lovligOpphold = lovligOpphold,
                    institusjonsopphold = InstitusjonsoppholdVilkår.IkkeVurdert,
                    personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert,
                    flyktning = FlyktningVilkår.IkkeVurdert,
                    fastOpphold = FastOppholdINorgeVilkår.IkkeVurdert,
                )
            }

            override fun erLik(other: Vilkårsvurderinger): Boolean {
                return other is Uføre && vilkår.erLik(other.vilkår)
            }

            /**
             * Override av [equals] for å slippe å endre alle eksisterende tester som baserer seg på objektliket.
             * Må modifiserers etterhvert som disse dataene begynner å lagres.
             */
            override fun equals(other: Any?): Boolean {
                return other is Uføre && erLik(other)
            }

            override fun lagTidslinje(periode: Periode): Uføre {
                return Uføre(
                    uføre = uføre.lagTidslinje(periode),
                    lovligOpphold = lovligOpphold.lagTidslinje(periode),
                    formue = formue.lagTidslinje(periode),
                    utenlandsopphold = utenlandsopphold.lagTidslinje(periode),
                    opplysningsplikt = opplysningsplikt.lagTidslinje(periode),
                )
            }

            fun oppdaterStønadsperiode(
                stønadsperiode: Stønadsperiode,
                formuegrenserFactory: FormuegrenserFactory,
            ): Uføre = Uføre(
                uføre = uføre.oppdaterStønadsperiode(stønadsperiode),
                lovligOpphold = lovligOpphold.oppdaterStønadsperiode(stønadsperiode),
                formue = formue.oppdaterStønadsperiode(stønadsperiode, formuegrenserFactory),
                utenlandsopphold = utenlandsopphold.oppdaterStønadsperiode(stønadsperiode),
                opplysningsplikt = opplysningsplikt.oppdaterStønadsperiode(stønadsperiode),
            )

            companion object {
                fun ikkeVurdert() = Uføre(
                    uføre = Vilkår.Uførhet.IkkeVurdert,
                    lovligOpphold = LovligOppholdVilkår.IkkeVurdert,
                    formue = Vilkår.Formue.IkkeVurdert,
                    utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
                    opplysningsplikt = OpplysningspliktVilkår.IkkeVurdert,
                )
            }
        }

        data class Alder(
            override val lovligOpphold: LovligOppholdVilkår,
            override val formue: Vilkår.Formue = Vilkår.Formue.IkkeVurdert,
            override val utenlandsopphold: UtenlandsoppholdVilkår = UtenlandsoppholdVilkår.IkkeVurdert,
            override val opplysningsplikt: OpplysningspliktVilkår = OpplysningspliktVilkår.IkkeVurdert,
            val pensjon: PensjonsVilkår,
            val familiegjenforening: FamiliegjenforeningVilkår,
        ) : Revurdering() {
            override val vilkår: Set<Vilkår> = setOf(
                formue,
                utenlandsopphold,
                opplysningsplikt,
                pensjon,
                familiegjenforening,
            )

            init {
                kastHvisPerioderErUlike()
            }

            override fun lagTidslinje(periode: Periode): Vilkårsvurderinger {
                return Alder(
                    lovligOpphold = lovligOpphold.lagTidslinje(periode),
                    formue = formue.lagTidslinje(periode),
                    utenlandsopphold = utenlandsopphold.lagTidslinje(periode),
                    opplysningsplikt = opplysningsplikt.lagTidslinje(periode),
                    pensjon = pensjon.lagTidslinje(periode),
                    familiegjenforening = familiegjenforening.lagTidslinje(periode),
                )
            }

            override fun leggTil(vilkår: Vilkår): Alder {
                return when (vilkår) {
                    is Vilkår.Formue -> copy(formue = vilkår)
                    is UtenlandsoppholdVilkår -> copy(utenlandsopphold = vilkår)
                    is OpplysningspliktVilkår -> copy(opplysningsplikt = vilkår)
                    is LovligOppholdVilkår -> copy(lovligOpphold = vilkår)
                    is FastOppholdINorgeVilkår,
                    is FlyktningVilkår,
                    is InstitusjonsoppholdVilkår,
                    is PersonligOppmøteVilkår,
                    -> {
                        throw IllegalArgumentException("Ukjent vilkår for revurdering av alder: ${vilkår::class}")
                    }
                    is Vilkår.Uførhet -> {
                        throw IllegalArgumentException("Kan ikke legge til ${vilkår::class} for ${this::class}")
                    }
                    is FamiliegjenforeningVilkår -> copy(familiegjenforening = vilkår)
                    is PensjonsVilkår -> copy(pensjon = vilkår)
                }
            }

            override fun tilVilkårsvurderingerRevurdering(): Revurdering {
                return this
            }

            override fun tilVilkårsvurderingerSøknadsbehandling(): Søknadsbehandling {
                return Søknadsbehandling.Alder(
                    formue = formue,
                    utenlandsopphold = utenlandsopphold,
                    opplysningsplikt = opplysningsplikt,
                    pensjon = pensjon,
                    familiegjenforening = familiegjenforening,
                    lovligOpphold = LovligOppholdVilkår.IkkeVurdert,
                    fastOpphold = FastOppholdINorgeVilkår.IkkeVurdert,
                    institusjonsopphold = InstitusjonsoppholdVilkår.IkkeVurdert,
                    personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert,
                )
            }

            override fun erLik(other: Vilkårsvurderinger): Boolean {
                return other is Alder && vilkår.erLik(other.vilkår)
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
    ) : Vilkårsvurderingsresultat() {
        val avslagsgrunner = vilkår.map { it.avslagsgrunn() }
        val tidligsteDatoForAvslag = vilkår.minOf { it.hentTidligesteDatoForAvslag()!! }

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
                is FamiliegjenforeningVilkår -> {
                    Avslagsgrunn.FAMILIEGJENFORENING
                }
                is PensjonsVilkår -> {
                    Avslagsgrunn.PENSJON
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
 * Et [Vilkår], dersom det er vurdert, er delt opp i 1 eller flere [Vurderingsperiode].
 * Hver enkelt [Vurderingsperiode] har en definert [Periode] og [Resultat], mens [Vilkår] har ikke disse entydige grensene:
 * - [no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling]: Et vilkår for en stønadsperiode må ha et entydig resultat og en sammenhengende periode.
 * - [no.nav.su.se.bakover.domain.revurdering.Revurdering] og [no.nav.su.se.bakover.domain.regulering.Regulering]: Kan gå på tvers av stønadsperioder og kan da bestå av flere enn et resultat og kan ha hull i periodene.
 * Revurdering/Regulering kan ha strengere regler enn dette i sine respektive implementasjoner.
 */
sealed class Vilkår {
    abstract val resultat: Resultat
    abstract val erAvslag: Boolean
    abstract val erInnvilget: Boolean
    abstract val vilkår: Inngangsvilkår

    /**
     * Vurderte vilkår vil ha en eller flere [Periode], mens ikkevurderte vilkår vil ikke ha en [Periode].
     * Periodene vil være sortert og vil ikke ha duplikater.
     * De skal også være slått sammen, der det er mulig.
     * Obs: Periodene kan fremdeles ha hull.
     */
    abstract val perioder: List<Periode>

    abstract fun hentTidligesteDatoForAvslag(): LocalDate?

    abstract fun erLik(other: Vilkår): Boolean
    abstract fun lagTidslinje(periode: Periode): Vilkår
    abstract fun slåSammenLikePerioder(): Vilkår

    protected fun kastHvisPerioderErUsortertEllerHarDuplikater() {
        require(perioder.erSortert())
        require(!perioder.harDuplikater())
        // TODO jah: Vurder å legg på require(perioder.minsteAntallSammenhengendePerioder() == perioder)
    }

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
            override val perioder: List<Periode> = emptyList()

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

            override val perioder: Nel<Periode> = vurderingsperioder.minsteAntallSammenhengendePerioder()

            init {
                kastHvisPerioderErUsortertEllerHarDuplikater()
            }

            override fun hentTidligesteDatoForAvslag(): LocalDate? {
                return vurderingsperioder.filter { it.resultat == Resultat.Avslag }.map { it.periode.fraOgMed }
                    .minByOrNull { it }
            }

            override fun erLik(other: Vilkår): Boolean {
                return other is Vurdert && vurderingsperioder.erLik(other.vurderingsperioder)
            }

            override fun slåSammenLikePerioder(): Vurdert {
                return Vurdert(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
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
                        Vurdert(vurderingsperioder = it.slåSammenLikePerioder())
                    }
                } else {
                    val tidligere = stønadsperiode.periode.starterTidligere(
                        vurderingsperioder.map { it.periode }
                            .minByOrNull { it.fraOgMed }!!,
                    )

                    if (tidligere) {
                        Vurdert(
                            vurderingsperioder = NonEmptyList.fromListUnsafe(
                                listOf(
                                    vurderingsperioder.minByOrNull { it.periode.fraOgMed }!!
                                        .oppdaterStønadsperiode(stønadsperiode),
                                ),
                            ).slåSammenLikePerioder(),
                        )
                    } else {
                        Vurdert(
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
                return Vurdert(
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
            override val perioder: List<Periode> = emptyList()

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

            override val perioder: Nel<Periode> = vurderingsperioder.minsteAntallSammenhengendePerioder()

            init {
                kastHvisPerioderErUsortertEllerHarDuplikater()
            }

            /** Merk at vi ikke kan garantere at det er hull i perioden */
            val periode: Periode = perioder.minAndMaxOf()

            override fun oppdaterStønadsperiode(
                stønadsperiode: Stønadsperiode,
                formuegrenserFactory: FormuegrenserFactory,
            ): Vurdert {
                check(vurderingsperioder.count() == 1) { "Kan ikke oppdatere stønadsperiode for vilkår med med enn én vurdering" }
                return Vurdert(
                    vurderingsperioder = this.vurderingsperioder.map {
                        it.oppdaterStønadsperiode(stønadsperiode, formuegrenserFactory)
                    },
                )
            }

            override fun lagTidslinje(periode: Periode): Vurdert {
                return Vurdert(
                    vurderingsperioder = Nel.fromListUnsafe(
                        Tidslinje(
                            periode = periode,
                            objekter = vurderingsperioder,
                        ).tidslinje,
                    ),
                )
            }

            override fun leggTilTomEPSFormueHvisDetMangler(perioder: List<Periode>): Vurdert {
                return Vurdert(
                    vurderingsperioder = vurderingsperioder.flatMap {
                        it.leggTilTomEPSFormueHvisDetMangler(perioder)
                    },
                )
            }

            override fun fjernEPSFormue(perioder: List<Periode>): Vurdert {
                return Vurdert(vurderingsperioder = vurderingsperioder.flatMap { it.fjernEPSFormue(perioder) })
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
                return Vurdert(vurderingsperioder = vurderingsperioder.slåSammenLikePerioder())
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

/**
 * Et [Vilkår], dersom det er vurdert, er delt opp i 1 eller flere [Vurderingsperiode].
 * Hver vurderingsperiode har en definert [Periode] og [Resultat], men trenger ikke å ha et grunnlag knyttet til seg.
 * I de fleste tilfeller er vurderingen gjort av en saksbehandler, men det finnes unntak, som [Formue] hvor systemet avgjør [Resultat] basert på grunnlagene.
 */
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
     * Slik at en vurderingsperiode kun kan opprettes med ett [Resultat].
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

fun Nel<Vurderingsperiode>.minsteAntallSammenhengendePerioder() =
    this.map { it.periode }.minsteAntallSammenhengendePerioder()

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
