package behandling.revurdering.domain

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

sealed interface VilkårsvurderingerRevurdering : Vilkårsvurderinger {
    abstract override val lovligOpphold: LovligOppholdVilkår
    abstract override val formue: FormueVilkår
    abstract override val utenlandsopphold: UtenlandsoppholdVilkår
    abstract override val opplysningsplikt: OpplysningspliktVilkår
    abstract override val fastOpphold: FastOppholdINorgeVilkår
    abstract override val institusjonsopphold: InstitusjonsoppholdVilkår

    fun oppdaterVilkår(vilkår: Vilkår): VilkårsvurderingerRevurdering

    override fun uføreVilkårKastHvisAlder(): UføreVilkår = when (this) {
        is Alder -> TODO("Kan ikke hente uføre vilkår. Vilkårsvurderinger for alder.")
        is Uføre -> uføre
    }

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

        override fun copyWithNewIds(): Uføre = Uføre(
            uføre = uføre.copyWithNewId() as UføreVilkår,
            lovligOpphold = lovligOpphold.copyWithNewId() as LovligOppholdVilkår,
            formue = formue.copyWithNewId() as FormueVilkår,
            utenlandsopphold = utenlandsopphold.copyWithNewId() as UtenlandsoppholdVilkår,
            opplysningsplikt = opplysningsplikt.copyWithNewId() as OpplysningspliktVilkår,
            flyktning = flyktning.copyWithNewId() as FlyktningVilkår,
            fastOpphold = fastOpphold.copyWithNewId() as FastOppholdINorgeVilkår,
            personligOppmøte = personligOppmøte.copyWithNewId() as PersonligOppmøteVilkår,
            institusjonsopphold = institusjonsopphold.copyWithNewId() as InstitusjonsoppholdVilkår,
        )

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

        override fun copyWithNewIds(): Alder = Alder(
            pensjon = pensjon.copyWithNewId() as PensjonsVilkår,
            lovligOpphold = lovligOpphold.copyWithNewId() as LovligOppholdVilkår,
            formue = formue.copyWithNewId() as FormueVilkår,
            utenlandsopphold = utenlandsopphold.copyWithNewId() as UtenlandsoppholdVilkår,
            opplysningsplikt = opplysningsplikt.copyWithNewId() as OpplysningspliktVilkår,
            familiegjenforening = familiegjenforening.copyWithNewId() as FamiliegjenforeningVilkår,
            fastOpphold = fastOpphold.copyWithNewId() as FastOppholdINorgeVilkår,
            personligOppmøte = personligOppmøte.copyWithNewId() as PersonligOppmøteVilkår,
            institusjonsopphold = institusjonsopphold.copyWithNewId() as InstitusjonsoppholdVilkår,
        )
    }
}
