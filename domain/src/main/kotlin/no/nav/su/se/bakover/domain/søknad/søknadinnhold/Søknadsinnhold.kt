package no.nav.su.se.bakover.domain.søknad.søknadinnhold

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold.Companion.validerBoforholdOgEktefelle

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = SøknadsinnholdAlder::class, name = "alder"),
    JsonSubTypes.Type(value = SøknadsinnholdUføre::class, name = "uføre"),
)
sealed interface SøknadInnhold {
    val personopplysninger: Personopplysninger
    val boforhold: Boforhold
    val utenlandsopphold: Utenlandsopphold
    val oppholdstillatelse: Oppholdstillatelse
    val inntektOgPensjon: InntektOgPensjon
    val formue: Formue
    val forNav: ForNav
    val ektefelle: Ektefelle?

    fun oppdaterFnr(fnr: Fnr): SøknadInnhold

    fun type() = when (this) {
        is SøknadsinnholdAlder -> Sakstype.ALDER
        is SøknadsinnholdUføre -> Sakstype.UFØRE
    }

    fun erPapirsøknad(): Boolean {
        return forNav.erPapirsøknad()
    }

    // slipper å måtte ha den 2 steder
    companion object {
        internal fun validerBoforholdOgEktefelle(
            boforhold: Boforhold,
            ektefelle: Ektefelle?,
        ) =
            if (boforhold.delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER && ektefelle == null) FeilVedValideringAvBoforholdOgEktefelle.EktefelleErIkkeutfylt.left() else Unit.right()
    }
}

data class SøknadsinnholdAlder private constructor(
    val harSøktAlderspensjon: HarSøktAlderspensjon,
    val oppholdstillatelseAlder: OppholdstillatelseAlder,
    override val personopplysninger: Personopplysninger,
    override val boforhold: Boforhold,
    override val utenlandsopphold: Utenlandsopphold,
    override val oppholdstillatelse: Oppholdstillatelse,
    override val inntektOgPensjon: InntektOgPensjon,
    override val formue: Formue,
    override val forNav: ForNav,
    override val ektefelle: Ektefelle?,
) : SøknadInnhold {

    companion object {
        fun tryCreate(
            harSøktAlderspensjon: HarSøktAlderspensjon,
            oppholdstillatelseAlder: OppholdstillatelseAlder,
            personopplysninger: Personopplysninger,
            boforhold: Boforhold,
            utenlandsopphold: Utenlandsopphold,
            oppholdstillatelse: Oppholdstillatelse,
            inntektOgPensjon: InntektOgPensjon,
            formue: Formue,
            forNav: ForNav,
            ektefelle: Ektefelle?,
        ): Either<FeilVedOpprettelseAvSøknadinnhold, SøknadsinnholdAlder> {
            valideroppholdstillatelseOgOppholdstillatelseAlder(oppholdstillatelse, oppholdstillatelseAlder).mapLeft {
                return FeilVedOpprettelseAvSøknadinnhold.DataVedOpphodlstillatelseErInkonsekvent(it).left()
            }
            validerBoforholdOgEktefelle(boforhold, ektefelle).mapLeft {
                return FeilVedOpprettelseAvSøknadinnhold.DataVedBoforholdOgEktefelleErInkonsekvent(it).left()
            }

            return SøknadsinnholdAlder(
                harSøktAlderspensjon = harSøktAlderspensjon,
                oppholdstillatelseAlder = oppholdstillatelseAlder,
                personopplysninger = personopplysninger,
                boforhold = boforhold,
                utenlandsopphold = utenlandsopphold,
                oppholdstillatelse = oppholdstillatelse,
                inntektOgPensjon = inntektOgPensjon,
                formue = formue,
                forNav = forNav,
                ektefelle = ektefelle,
            ).right()
        }

        private fun valideroppholdstillatelseOgOppholdstillatelseAlder(
            oppholdstillatelse: Oppholdstillatelse,
            oppholdstillatelseAlder: OppholdstillatelseAlder,
        ): Either<FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder, Unit> {
            validerEøsBorger(oppholdstillatelse, oppholdstillatelseAlder).mapLeft { return it.left() }
            validerFamiliegjenforening(oppholdstillatelse, oppholdstillatelseAlder).mapLeft { return it.left() }

            return Unit.right()
        }

        private fun validerEøsBorger(
            oppholdstillatelse: Oppholdstillatelse,
            oppholdstillatelseAlder: OppholdstillatelseAlder,
        ) =
            if (!oppholdstillatelse.erNorskStatsborger && oppholdstillatelseAlder.eøsborger == null) FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder.EøsBorgerErIkkeutfylt.left() else Unit.right()

        private fun validerFamiliegjenforening(
            oppholdstillatelse: Oppholdstillatelse,
            oppholdstillatelseAlder: OppholdstillatelseAlder,
        ) =
            if (!oppholdstillatelse.erNorskStatsborger && oppholdstillatelse.harOppholdstillatelse == true && oppholdstillatelseAlder.familiegjenforening == null) FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder.FamiliegjenforeningErIkkeutfylt.left() else Unit.right()
    }

    override fun oppdaterFnr(fnr: Fnr): SøknadsinnholdAlder {
        return this.copy(
            personopplysninger = Personopplysninger(fnr),
        )
    }
}

data class SøknadsinnholdUføre private constructor(
    val uførevedtak: Uførevedtak,
    val flyktningsstatus: Flyktningsstatus,
    override val personopplysninger: Personopplysninger,
    override val boforhold: Boforhold,
    override val utenlandsopphold: Utenlandsopphold,
    override val oppholdstillatelse: Oppholdstillatelse,
    override val inntektOgPensjon: InntektOgPensjon,
    override val formue: Formue,
    override val forNav: ForNav,
    override val ektefelle: Ektefelle?,
) : SøknadInnhold {
    companion object {
        fun tryCreate(
            uførevedtak: Uførevedtak,
            flyktningsstatus: Flyktningsstatus,
            personopplysninger: Personopplysninger,
            boforhold: Boforhold,
            utenlandsopphold: Utenlandsopphold,
            oppholdstillatelse: Oppholdstillatelse,
            inntektOgPensjon: InntektOgPensjon,
            formue: Formue,
            forNav: ForNav,
            ektefelle: Ektefelle?,
        ): Either<FeilVedOpprettelseAvSøknadinnhold, SøknadsinnholdUføre> {
            validerBoforholdOgEktefelle(boforhold, ektefelle).mapLeft {
                return FeilVedOpprettelseAvSøknadinnhold.DataVedBoforholdOgEktefelleErInkonsekvent(it).left()
            }

            return SøknadsinnholdUføre(
                uførevedtak = uførevedtak,
                flyktningsstatus = flyktningsstatus,
                personopplysninger = personopplysninger,
                boforhold = boforhold,
                utenlandsopphold = utenlandsopphold,
                oppholdstillatelse = oppholdstillatelse,
                inntektOgPensjon = inntektOgPensjon,
                formue = formue,
                forNav = forNav,
                ektefelle = ektefelle,
            ).right()
        }
    }
    override fun oppdaterFnr(fnr: Fnr): SøknadsinnholdUføre {
        return this.copy(
            personopplysninger = Personopplysninger(fnr),
        )
    }
}

sealed interface FeilVedOpprettelseAvSøknadinnhold {
    data class DataVedOpphodlstillatelseErInkonsekvent(val underliggendeFeil: FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder) : FeilVedOpprettelseAvSøknadinnhold

    data class DataVedBoforholdOgEktefelleErInkonsekvent(val underliggendeFeil: FeilVedValideringAvBoforholdOgEktefelle) : FeilVedOpprettelseAvSøknadinnhold
}

sealed interface FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder {
    data object EøsBorgerErIkkeutfylt : FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder
    data object FamiliegjenforeningErIkkeutfylt : FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder
}

sealed interface FeilVedValideringAvBoforholdOgEktefelle {
    data object EktefelleErIkkeutfylt : FeilVedValideringAvBoforholdOgEktefelle
}
