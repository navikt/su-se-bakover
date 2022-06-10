package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.søknadinnhold.Boforhold
import no.nav.su.se.bakover.domain.søknadinnhold.Ektefelle
import no.nav.su.se.bakover.domain.søknadinnhold.FeilVedOpprettelseAvBoforhold
import no.nav.su.se.bakover.domain.søknadinnhold.FeilVedOpprettelseAvFormue
import no.nav.su.se.bakover.domain.søknadinnhold.FeilVedOpprettelseAvOppholdstillatelse
import no.nav.su.se.bakover.domain.søknadinnhold.Oppholdstillatelse
import no.nav.su.se.bakover.domain.søknadinnhold.OppholdstillatelseAlder
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadsinnholdAlder
import no.nav.su.se.bakover.domain.søknadinnhold.SøknadsinnholdUføre
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.BoforholdJson.Companion.toBoforholdJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.EktefelleJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.FlyktningsstatusJson.Companion.toFlyktningsstatusJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.ForNavJson.Companion.toForNavJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.FormueJson.Companion.toFormueJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.HarSøktAlderspensjonJson.Companion.toHarSøktAlderspensjonJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.InntektOgPensjonJson.Companion.toInntektOgPensjonJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.OppholdstillatelseAlderJson.Companion.toOppholdstillatelseAlderJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.OppholdstillatelseJson.Companion.toOppholdstillatelseJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.PersonopplysningerJson.Companion.toPersonopplysningerJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdAlderJson.Companion.toSøknadsinnholdAlderJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdUføreJson.Companion.toSøknadsinnholdUføreJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.UførevedtakJson.Companion.toUførevedtakJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.UtenlandsoppholdJson.Companion.toUtenlandsoppholdJson

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type",
)
@JsonSubTypes(
    JsonSubTypes.Type(value = SøknadsinnholdAlderJson::class, name = "alder"),
    JsonSubTypes.Type(value = SøknadsinnholdUføreJson::class, name = "uføre"),
)

sealed class SøknadsinnholdJson {
    abstract val personopplysninger: PersonopplysningerJson
    abstract val boforhold: BoforholdJson
    abstract val utenlandsopphold: UtenlandsoppholdJson
    abstract val oppholdstillatelse: OppholdstillatelseJson
    abstract val inntektOgPensjon: InntektOgPensjonJson
    abstract val formue: FormueJson
    abstract val forNav: ForNavJson
    abstract val ektefelle: EktefelleJson?

    fun toSøknadsinnhold() = when (this) {
        is SøknadsinnholdAlderJson -> toSøknadsinnholdAlder()
        is SøknadsinnholdUføreJson -> toSøknadsinnholdUføre()
    }

    protected fun validerEktefelle(
        boforhold: Boforhold,
        ektefelle: Ektefelle?,
    ): Either<FeilVedValideringAvBoforholdOgEktefelle, Unit> {
        return if (boforhold.delerBoligMed == Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER && ektefelle == null) FeilVedValideringAvBoforholdOgEktefelle.EktefelleErIkkeutfylt.left() else Unit.right()
    }

    companion object {
        fun SøknadInnhold.toSøknadsinnholdJson() = when (this) {
            is SøknadsinnholdAlder -> toSøknadsinnholdAlderJson()
            is SøknadsinnholdUføre -> toSøknadsinnholdUføreJson()
        }
    }
}

data class SøknadsinnholdAlderJson(
    val harSøktAlderspensjon: HarSøktAlderspensjonJson,
    val oppholdstillatelseAlder: OppholdstillatelseAlderJson,
    override val personopplysninger: PersonopplysningerJson,
    override val boforhold: BoforholdJson,
    override val utenlandsopphold: UtenlandsoppholdJson,
    override val oppholdstillatelse: OppholdstillatelseJson,
    override val inntektOgPensjon: InntektOgPensjonJson,
    override val formue: FormueJson,
    override val forNav: ForNavJson,
    override val ektefelle: EktefelleJson?,
) : SøknadsinnholdJson() {

    fun toSøknadsinnholdAlder(): Either<FeilVedOpprettelseAvSøknadinnhold, SøknadsinnholdAlder> {
        val oppholdstillatelse = oppholdstillatelse.toOppholdstillatelse().getOrHandle {
            return FeilVedOpprettelseAvSøknadinnhold.FeilVedOpprettelseAvOppholdstillatelseWeb(it).left()
        }
        val oppholdstillatelseAlder = oppholdstillatelseAlder.toOppholdstillatelseAlder()
        val boforhold = boforhold.toBoforhold().getOrHandle {
            return FeilVedOpprettelseAvSøknadinnhold.FeilVedOpprettelseAvBoforholdWeb(it).left()
        }
        val ektefelle = ektefelle?.toEktefelle()?.getOrHandle {
            return FeilVedOpprettelseAvSøknadinnhold.FeilVedOpprettelseAvEktefelleWeb(it).left()
        }

        validerOppholdstillatelseAlder(oppholdstillatelse, oppholdstillatelseAlder).mapLeft {
            return FeilVedOpprettelseAvSøknadinnhold.DataVedOpphodlstillatelseErInkonsekvent(it).left()
        }
        validerEktefelle(boforhold, ektefelle).mapLeft {
            return FeilVedOpprettelseAvSøknadinnhold.DataVedBoforholdOgEktefelleErInkonsekvent(it).left()
        }

        return SøknadsinnholdAlder(
            harSøktAlderspensjon = harSøktAlderspensjon.toHarSøktAlderspensjon(),
            oppholdstillatelseAlder = oppholdstillatelseAlder,
            personopplysninger = personopplysninger.toPersonopplysninger(),
            boforhold = boforhold,
            utenlandsopphold = utenlandsopphold.toUtenlandsopphold(),
            oppholdstillatelse = oppholdstillatelse,
            inntektOgPensjon = inntektOgPensjon.toInntektOgPensjon(),
            formue = formue.toFormue().getOrHandle {
                return FeilVedOpprettelseAvSøknadinnhold.FeilVedOpprettelseAvFormueWeb(it).left()
            },
            forNav = forNav.toForNav(),
            ektefelle = ektefelle,
        ).right()
    }

    private fun validerOppholdstillatelseAlder(
        oppholdstillatelse: Oppholdstillatelse,
        oppholdstillatelseAlder: OppholdstillatelseAlder,
    ): Either<FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder, Unit> {
        validerEøsBorger(oppholdstillatelse, oppholdstillatelseAlder).mapLeft {
            return it.left()
        }
        validerFamiliegjenforening(oppholdstillatelse, oppholdstillatelseAlder).mapLeft {
            return it.left()
        }

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

    companion object {
        fun SøknadsinnholdAlder.toSøknadsinnholdAlderJson() = SøknadsinnholdAlderJson(
            harSøktAlderspensjon = harSøktAlderspensjon.toHarSøktAlderspensjonJson(),
            oppholdstillatelseAlder = oppholdstillatelseAlder.toOppholdstillatelseAlderJson(),
            personopplysninger = personopplysninger.toPersonopplysningerJson(),
            boforhold = boforhold.toBoforholdJson(),
            utenlandsopphold = utenlandsopphold.toUtenlandsoppholdJson(),
            oppholdstillatelse = oppholdstillatelse.toOppholdstillatelseJson(),
            inntektOgPensjon = inntektOgPensjon.toInntektOgPensjonJson(),
            formue = formue.toFormueJson(),
            forNav = forNav.toForNavJson(),
            ektefelle = ektefelle?.toJson(),
        )
    }
}

data class SøknadsinnholdUføreJson(
    val uførevedtak: UførevedtakJson,
    val flyktningsstatus: FlyktningsstatusJson,
    override val personopplysninger: PersonopplysningerJson,
    override val boforhold: BoforholdJson,
    override val utenlandsopphold: UtenlandsoppholdJson,
    override val oppholdstillatelse: OppholdstillatelseJson,
    override val inntektOgPensjon: InntektOgPensjonJson,
    override val formue: FormueJson,
    override val forNav: ForNavJson,
    override val ektefelle: EktefelleJson?,
) : SøknadsinnholdJson() {

    fun toSøknadsinnholdUføre(): Either<FeilVedOpprettelseAvSøknadinnhold, SøknadsinnholdUføre> {
        val boforhold = boforhold.toBoforhold().getOrHandle {
            return FeilVedOpprettelseAvSøknadinnhold.FeilVedOpprettelseAvBoforholdWeb(it).left()
        }
        val ektefelle = ektefelle?.toEktefelle()?.getOrHandle {
            return FeilVedOpprettelseAvSøknadinnhold.FeilVedOpprettelseAvEktefelleWeb(it).left()
        }
        validerEktefelle(boforhold, ektefelle).mapLeft {
            return FeilVedOpprettelseAvSøknadinnhold.DataVedBoforholdOgEktefelleErInkonsekvent(it).left()
        }

        return SøknadsinnholdUføre(
            uførevedtak = uførevedtak.toUførevedtak(),
            personopplysninger = personopplysninger.toPersonopplysninger(),
            flyktningsstatus = flyktningsstatus.toFlyktningsstatus(),
            boforhold = boforhold,
            utenlandsopphold = utenlandsopphold.toUtenlandsopphold(),
            oppholdstillatelse = oppholdstillatelse.toOppholdstillatelse().getOrHandle {
                return FeilVedOpprettelseAvSøknadinnhold.FeilVedOpprettelseAvOppholdstillatelseWeb(it).left()
            },
            inntektOgPensjon = inntektOgPensjon.toInntektOgPensjon(),
            formue = formue.toFormue().getOrHandle {
                return FeilVedOpprettelseAvSøknadinnhold.FeilVedOpprettelseAvFormueWeb(it).left()
            },
            forNav = forNav.toForNav(),
            ektefelle = ektefelle,
        ).right()
    }

    companion object {
        fun SøknadsinnholdUføre.toSøknadsinnholdUføreJson() = SøknadsinnholdUføreJson(
            uførevedtak = uførevedtak.toUførevedtakJson(),
            personopplysninger = personopplysninger.toPersonopplysningerJson(),
            flyktningsstatus = flyktningsstatus.toFlyktningsstatusJson(),
            boforhold = boforhold.toBoforholdJson(),
            utenlandsopphold = utenlandsopphold.toUtenlandsoppholdJson(),
            oppholdstillatelse = oppholdstillatelse.toOppholdstillatelseJson(),
            inntektOgPensjon = inntektOgPensjon.toInntektOgPensjonJson(),
            formue = formue.toFormueJson(),
            forNav = forNav.toForNavJson(),
            ektefelle = ektefelle?.toJson(),
        )
    }
}

sealed interface FeilVedOpprettelseAvSøknadinnhold {
    data class FeilVedOpprettelseAvOppholdstillatelseWeb(val underliggendeFeil: FeilVedOpprettelseAvOppholdstillatelse) :
        FeilVedOpprettelseAvSøknadinnhold

    data class FeilVedOpprettelseAvBoforholdWeb(val underliggendeFeil: FeilVedOpprettelseAvBoforhold) :
        FeilVedOpprettelseAvSøknadinnhold

    data class FeilVedOpprettelseAvFormueWeb(val underliggendeFeil: FeilVedOpprettelseAvFormue) :
        FeilVedOpprettelseAvSøknadinnhold

    data class FeilVedOpprettelseAvEktefelleWeb(val underliggendeFeil: FeilVedOpprettelseAvEktefelleJson) :
        FeilVedOpprettelseAvSøknadinnhold

    data class DataVedOpphodlstillatelseErInkonsekvent(val underliggendeFeil: FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder) :
        FeilVedOpprettelseAvSøknadinnhold

    data class DataVedBoforholdOgEktefelleErInkonsekvent(val underliggendeFeil: FeilVedValideringAvBoforholdOgEktefelle) :
        FeilVedOpprettelseAvSøknadinnhold
}

sealed interface FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder {
    object EøsBorgerErIkkeutfylt : FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder
    object FamiliegjenforeningErIkkeutfylt : FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder
}

sealed interface FeilVedValideringAvBoforholdOgEktefelle {
    object EktefelleErIkkeutfylt : FeilVedValideringAvBoforholdOgEktefelle
}
