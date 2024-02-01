package behandling.søknadsbehandling.domain

import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.vilkår.InstitusjonsoppholdVilkår
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
import vilkår.vurderinger.domain.Vilkårsvurderinger
import vilkår.vurderinger.domain.kastHvisPerioderErUlike

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

    override fun uføreVilkårKastHvisAlder(): UføreVilkår = when (this) {
        is Uføre -> uføre
        is Alder -> TODO("Kan ikke hente uføre vilkår. Vilkårsvurderinger for alder.")
    }

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
