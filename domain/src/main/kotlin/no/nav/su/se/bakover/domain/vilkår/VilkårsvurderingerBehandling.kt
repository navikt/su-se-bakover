package no.nav.su.se.bakover.domain.vilkår

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.vilkår.VurderingsperiodeInstitusjonsopphold.Companion.equals
import no.nav.su.se.bakover.utenlandsopphold.domain.vilkår.UtenlandsoppholdVilkår
import vilkår.common.domain.Vilkår
import vilkår.common.domain.erLik
import vilkår.familiegjenforening.domain.FamiliegjenforeningVilkår
import vilkår.fastopphold.domain.FastOppholdINorgeVilkår
import vilkår.flyktning.domain.FlyktningVilkår
import vilkår.formue.domain.FormueVilkår
import vilkår.formue.domain.FormuegrenserFactory
import vilkår.lovligopphold.domain.LovligOppholdVilkår
import vilkår.opplysningsplikt.domain.OpplysningspliktVilkår
import vilkår.pensjon.domain.PensjonsVilkår
import vilkår.personligoppmøte.domain.PersonligOppmøteVilkår
import vilkår.uføre.domain.UføreVilkår
import vilkår.uføre.domain.Uføregrunnlag
import vilkår.vurderinger.domain.VilkårEksistererIkke
import vilkår.vurderinger.domain.Vilkårsvurderinger

fun Vilkårsvurderinger.uføreVilkår(): Either<VilkårEksistererIkke, UføreVilkår> = when (this) {
    is VilkårsvurderingerRevurdering.Alder -> VilkårEksistererIkke.left()
    is VilkårsvurderingerRevurdering.Uføre -> uføre.right()
    is VilkårsvurderingerSøknadsbehandling.Uføre -> uføre.right()
    is VilkårsvurderingerSøknadsbehandling.Alder -> VilkårEksistererIkke.left()
    else -> throw IllegalStateException("Ukjent vilkårsvurderings-implementasjon: $this")
}

fun Vilkårsvurderinger.uføreVilkårKastHvisAlder(): UføreVilkår = when (this) {
    is VilkårsvurderingerRevurdering.Alder -> TODO("vilkårsvurdering_alder konsistenssjekk for alder")
    is VilkårsvurderingerRevurdering.Uføre -> uføre
    is VilkårsvurderingerSøknadsbehandling.Uføre -> uføre
    is VilkårsvurderingerSøknadsbehandling.Alder -> TODO("vilkårsvurdering_alder konsistenssjekk for alder")
    else -> throw IllegalStateException("Ukjent vilkårsvurderings-implementasjon: $this")
}

fun Vilkårsvurderinger.flyktningVilkår(): Either<VilkårEksistererIkke, FlyktningVilkår> = when (this) {
    is VilkårsvurderingerRevurdering.Alder -> VilkårEksistererIkke.left()
    is VilkårsvurderingerRevurdering.Uføre -> flyktning.right()
    is VilkårsvurderingerSøknadsbehandling.Alder -> VilkårEksistererIkke.left()
    is VilkårsvurderingerSøknadsbehandling.Uføre -> flyktning.right()
    else -> throw IllegalStateException("Ukjent vilkårsvurderings-implementasjon: $this")
}

fun Vilkårsvurderinger.pensjonsVilkår(): Either<VilkårEksistererIkke, PensjonsVilkår> = when (this) {
    is VilkårsvurderingerRevurdering.Alder -> pensjon.right()
    is VilkårsvurderingerRevurdering.Uføre -> VilkårEksistererIkke.left()
    is VilkårsvurderingerSøknadsbehandling.Alder -> pensjon.right()
    is VilkårsvurderingerSøknadsbehandling.Uføre -> VilkårEksistererIkke.left()
    else -> throw IllegalStateException("Ukjent vilkårsvurderings-implementasjon: $this")
}

fun Vilkårsvurderinger.familiegjenforening(): Either<VilkårEksistererIkke, FamiliegjenforeningVilkår> = when (this) {
    is VilkårsvurderingerRevurdering.Alder -> familiegjenforening.right()
    is VilkårsvurderingerRevurdering.Uføre -> VilkårEksistererIkke.left()
    is VilkårsvurderingerSøknadsbehandling.Alder -> familiegjenforening.right()
    is VilkårsvurderingerSøknadsbehandling.Uføre -> VilkårEksistererIkke.left()
    else -> throw IllegalStateException("Ukjent vilkårsvurderings-implementasjon: $this")
}

sealed interface VilkårsvurderingerSøknadsbehandling : Vilkårsvurderinger {
    abstract override val formue: FormueVilkår
    abstract override val lovligOpphold: LovligOppholdVilkår
    abstract override val fastOpphold: FastOppholdINorgeVilkår
    abstract override val institusjonsopphold: InstitusjonsoppholdVilkår
    abstract override val utenlandsopphold: UtenlandsoppholdVilkår
    abstract override val opplysningsplikt: OpplysningspliktVilkår

    fun oppdaterVilkår(vilkår: Vilkår): VilkårsvurderingerSøknadsbehandling

    fun oppdaterStønadsperiode(
        stønadsperiode: Stønadsperiode,
        formuegrenserFactory: FormuegrenserFactory,
    ): VilkårsvurderingerSøknadsbehandling

    data class Uføre(
        override val formue: FormueVilkår,
        override val lovligOpphold: LovligOppholdVilkår,
        override val fastOpphold: FastOppholdINorgeVilkår,
        override val institusjonsopphold: InstitusjonsoppholdVilkår,
        override val utenlandsopphold: UtenlandsoppholdVilkår,
        override val personligOppmøte: PersonligOppmøteVilkår,
        override val opplysningsplikt: OpplysningspliktVilkår,
        val uføre: UføreVilkår,
        val flyktning: FlyktningVilkår,
    ) : VilkårsvurderingerSøknadsbehandling {
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

        override fun oppdaterVilkår(vilkår: Vilkår): Uføre {
            return when (vilkår) {
                is FastOppholdINorgeVilkår -> copy(fastOpphold = vilkår)
                is FlyktningVilkår -> copy(flyktning = vilkår)
                is FormueVilkår -> copy(formue = vilkår)
                is InstitusjonsoppholdVilkår -> copy(institusjonsopphold = vilkår)
                is LovligOppholdVilkår -> copy(lovligOpphold = vilkår)
                is UtenlandsoppholdVilkår -> copy(utenlandsopphold = vilkår)
                is PersonligOppmøteVilkår -> copy(personligOppmøte = vilkår)
                is UføreVilkår -> copy(uføre = vilkår)
                is OpplysningspliktVilkår -> copy(opplysningsplikt = vilkår)
                is FamiliegjenforeningVilkår -> throw IllegalArgumentException("Kan ikke legge til FamiliegjenforeningVilkår for vilkårsvurdering uføre (kun støttet for alder)")
                is PensjonsVilkår -> throw IllegalArgumentException("Kan ikke legge til Pensjonsvilkår for vilkårvurdering uføre (kun støttet for alder)")
                else -> throw IllegalStateException("Ukjent vilkår: $vilkår ved oppdatering av vilkårsvurderinger for uføre (søknadsbehandling)")
            }
        }

        fun tilVilkårsvurderingerRevurdering(): VilkårsvurderingerRevurdering.Uføre {
            return VilkårsvurderingerRevurdering.Uføre(
                uføre = uføre,
                lovligOpphold = lovligOpphold,
                formue = formue,
                utenlandsopphold = utenlandsopphold,
                opplysningsplikt = opplysningsplikt,
                flyktning = flyktning,
                fastOpphold = fastOpphold,
                personligOppmøte = personligOppmøte,
                institusjonsopphold = institusjonsopphold,
            )
        }

        fun tilVilkårsvurderingerSøknadsbehandling(): Uføre {
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
                uføre = UføreVilkår.IkkeVurdert,
                formue = FormueVilkår.IkkeVurdert,
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
        override val formue: FormueVilkår,
        override val lovligOpphold: LovligOppholdVilkår,
        override val fastOpphold: FastOppholdINorgeVilkår,
        override val institusjonsopphold: InstitusjonsoppholdVilkår,
        override val utenlandsopphold: UtenlandsoppholdVilkår,
        override val personligOppmøte: PersonligOppmøteVilkår,
        override val opplysningsplikt: OpplysningspliktVilkår,
        val pensjon: PensjonsVilkår = PensjonsVilkår.IkkeVurdert,
        val familiegjenforening: FamiliegjenforeningVilkår,
    ) : VilkårsvurderingerSøknadsbehandling {
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

        override fun lagTidslinje(periode: Periode): VilkårsvurderingerSøknadsbehandling {
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

        override fun oppdaterVilkår(vilkår: Vilkår): Alder {
            return when (vilkår) {
                is FastOppholdINorgeVilkår -> copy(fastOpphold = vilkår)
                is FormueVilkår -> copy(formue = vilkår)
                is InstitusjonsoppholdVilkår -> copy(institusjonsopphold = vilkår)
                is LovligOppholdVilkår -> copy(lovligOpphold = vilkår)
                is OpplysningspliktVilkår -> copy(opplysningsplikt = vilkår)
                is PersonligOppmøteVilkår -> copy(personligOppmøte = vilkår)
                is UtenlandsoppholdVilkår -> copy(utenlandsopphold = vilkår)
                is FamiliegjenforeningVilkår -> copy(familiegjenforening = vilkår)
                is PensjonsVilkår -> copy(pensjon = vilkår)
                is FlyktningVilkår -> throw IllegalArgumentException("Kan ikke legge til flyktningvilkår for vilkårsvurdering alder (støttes kun av ufør flyktning)")
                is UføreVilkår -> throw IllegalArgumentException("Kan ikke legge til uførevilkår for vilkårsvurdering alder (støttes kun av ufør flyktning)")
                else -> throw IllegalStateException("Ukjent vilkår: $vilkår ved oppdatering av vilkårsvurderinger for alder (søknadsbehandling)")
            }
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
            familiegjenforening = familiegjenforening.oppdaterStønadsperiode(stønadsperiode),
        )

        override fun erLik(other: Vilkårsvurderinger): Boolean {
            return other is Alder && vilkår.erLik(other.vilkår)
        }

        companion object {
            fun ikkeVurdert() = Alder(
                formue = FormueVilkår.IkkeVurdert,
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

sealed interface VilkårsvurderingerRevurdering : Vilkårsvurderinger {
    abstract override val lovligOpphold: LovligOppholdVilkår
    abstract override val formue: FormueVilkår
    abstract override val utenlandsopphold: UtenlandsoppholdVilkår
    abstract override val opplysningsplikt: OpplysningspliktVilkår
    abstract override val fastOpphold: FastOppholdINorgeVilkår
    abstract override val institusjonsopphold: InstitusjonsoppholdVilkår

    fun oppdaterVilkår(vilkår: Vilkår): VilkårsvurderingerRevurdering

    data class Uføre(
        val uføre: UføreVilkår,
        override val lovligOpphold: LovligOppholdVilkår,
        override val formue: FormueVilkår,
        override val utenlandsopphold: UtenlandsoppholdVilkår,
        override val opplysningsplikt: OpplysningspliktVilkår,
        val flyktning: FlyktningVilkår,
        override val fastOpphold: FastOppholdINorgeVilkår,
        override val personligOppmøte: PersonligOppmøteVilkår,
        override val institusjonsopphold: InstitusjonsoppholdVilkår,
    ) : VilkårsvurderingerRevurdering {

        override val vilkår: Set<Vilkår> = setOf(
            uføre,
            formue,
            utenlandsopphold,
            opplysningsplikt,
            lovligOpphold,
            flyktning,
            fastOpphold,
            personligOppmøte,
            institusjonsopphold,
        )

        init {
            kastHvisPerioderErUlike()
        }

        override fun oppdaterVilkår(vilkår: Vilkår): Uføre {
            return when (vilkår) {
                is FormueVilkår -> copy(formue = vilkår)
                is UføreVilkår -> copy(uføre = vilkår)
                is UtenlandsoppholdVilkår -> copy(utenlandsopphold = vilkår)
                is OpplysningspliktVilkår -> copy(opplysningsplikt = vilkår)
                is LovligOppholdVilkår -> copy(lovligOpphold = vilkår)
                is FlyktningVilkår -> copy(flyktning = vilkår)
                is PersonligOppmøteVilkår -> copy(personligOppmøte = vilkår)
                is FastOppholdINorgeVilkår -> copy(fastOpphold = vilkår)
                is InstitusjonsoppholdVilkår -> copy(institusjonsopphold = vilkår)
                is FamiliegjenforeningVilkår -> throw IllegalArgumentException("Kan ikke legge til FamiliegjenforeningVilkår for vilkårsvurdering uføre (kun støttet for alder)")
                is PensjonsVilkår -> throw IllegalArgumentException("Kan ikke legge til Pensjonsvilkår for vilkårvurdering uføre (kun støttet for alder)")
                else -> throw IllegalStateException("Ukjent vilkår: $vilkår ved oppdatering av vilkårsvurderinger for uføre (revurdering)")
            }
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
                flyktning = flyktning.lagTidslinje(periode),
                fastOpphold = fastOpphold.lagTidslinje(periode),
                personligOppmøte = personligOppmøte.lagTidslinje(periode),
                institusjonsopphold = institusjonsopphold.lagTidslinje(periode),
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
            flyktning = flyktning.oppdaterStønadsperiode(stønadsperiode),
            fastOpphold = fastOpphold.oppdaterStønadsperiode(stønadsperiode),
            personligOppmøte = personligOppmøte.oppdaterStønadsperiode(stønadsperiode),
            institusjonsopphold = institusjonsopphold.lagTidslinje(stønadsperiode.periode),
        )

        companion object {
            fun ikkeVurdert() = Uføre(
                uføre = UføreVilkår.IkkeVurdert,
                lovligOpphold = LovligOppholdVilkår.IkkeVurdert,
                formue = FormueVilkår.IkkeVurdert,
                utenlandsopphold = UtenlandsoppholdVilkår.IkkeVurdert,
                opplysningsplikt = OpplysningspliktVilkår.IkkeVurdert,
                institusjonsopphold = InstitusjonsoppholdVilkår.IkkeVurdert,
                flyktning = FlyktningVilkår.IkkeVurdert,
                fastOpphold = FastOppholdINorgeVilkår.IkkeVurdert,
                personligOppmøte = PersonligOppmøteVilkår.IkkeVurdert,
            )
        }
    }

    data class Alder(
        override val lovligOpphold: LovligOppholdVilkår,
        override val formue: FormueVilkår = FormueVilkår.IkkeVurdert,
        override val utenlandsopphold: UtenlandsoppholdVilkår = UtenlandsoppholdVilkår.IkkeVurdert,
        override val opplysningsplikt: OpplysningspliktVilkår = OpplysningspliktVilkår.IkkeVurdert,
        override val institusjonsopphold: InstitusjonsoppholdVilkår = InstitusjonsoppholdVilkår.IkkeVurdert,
        val pensjon: PensjonsVilkår,
        val familiegjenforening: FamiliegjenforeningVilkår,
        override val fastOpphold: FastOppholdINorgeVilkår,
        override val personligOppmøte: PersonligOppmøteVilkår,
    ) : VilkårsvurderingerRevurdering {
        override val vilkår: Set<Vilkår> = setOf(
            formue,
            utenlandsopphold,
            opplysningsplikt,
            pensjon,
            familiegjenforening,
            lovligOpphold,
            fastOpphold,
            personligOppmøte,
            institusjonsopphold,
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
                fastOpphold = fastOpphold.lagTidslinje(periode),
                personligOppmøte = personligOppmøte.lagTidslinje(periode),
            )
        }

        override fun oppdaterVilkår(vilkår: Vilkår): Alder {
            return when (vilkår) {
                is FormueVilkår -> copy(formue = vilkår)
                is UtenlandsoppholdVilkår -> copy(utenlandsopphold = vilkår)
                is OpplysningspliktVilkår -> copy(opplysningsplikt = vilkår)
                is LovligOppholdVilkår -> copy(lovligOpphold = vilkår)
                is PersonligOppmøteVilkår -> copy(personligOppmøte = vilkår)
                is FastOppholdINorgeVilkår -> copy(fastOpphold = vilkår)
                is InstitusjonsoppholdVilkår -> copy(institusjonsopphold = vilkår)
                is FamiliegjenforeningVilkår -> copy(familiegjenforening = vilkår)
                is PensjonsVilkår -> copy(pensjon = vilkår)
                is FlyktningVilkår -> throw IllegalArgumentException("Kan ikke legge til flyktningvilkår for vilkårsvurdering alder (støttes kun av ufør flyktning)")
                is UføreVilkår -> throw IllegalArgumentException("Kan ikke legge til uførevilkår for vilkårsvurdering alder (støttes kun av ufør flyktning)")
                else -> throw IllegalStateException("Ukjent vilkår: $vilkår ved oppdatering av vilkårsvurderinger for alder (revurdering)")
            }
        }

        override fun erLik(other: Vilkårsvurderinger): Boolean {
            return other is Alder && vilkår.erLik(other.vilkår)
        }
    }
}

/**
 * Skal kun kalles fra undertyper av [Vilkårsvurderinger].
 */
internal fun Vilkårsvurderinger.kastHvisPerioderErUlike() {
    // Merk at hvert enkelt [Vilkår] passer på sine egne data (som f.eks. at periodene er sorterte og uten duplikater)
    vilkår.map { Pair(it.vilkår, it.perioder) }.zipWithNext { a, b ->
        // Vilkår med tomme perioder har ikke blitt vurdert enda.
        if (a.second.isNotEmpty() && b.second.isNotEmpty()) {
            require(a.second == b.second) {
                "Periodene til Vilkårsvurderinger er ulike. $a vs $b."
            }
        }
    }
}

/**
 * @throws NotImplementedError for alder
 */
fun Vilkårsvurderinger.hentUføregrunnlag(): List<Uføregrunnlag> = when (this) {
    is VilkårsvurderingerRevurdering.Uføre -> this.uføre.grunnlag
    is VilkårsvurderingerSøknadsbehandling.Uføre -> this.uføre.grunnlag
    is VilkårsvurderingerRevurdering.Alder -> TODO("vilkårsvurdering_alder brev for alder ikke implementert enda")
    is VilkårsvurderingerSøknadsbehandling.Alder -> emptyList() // TODO("vilkårsvurdering_alder brev for alder ikke implementert enda")
    else -> throw IllegalStateException("Ukjent vilkårsvurderings-implementasjon: $this")
}
