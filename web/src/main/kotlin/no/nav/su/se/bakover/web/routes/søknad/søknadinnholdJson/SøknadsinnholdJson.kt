package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FeilVedOpprettelseAvBoforhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FeilVedOpprettelseAvFormue
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FeilVedOpprettelseAvOppholdstillatelse
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.FeilVedOpprettelseAvSøknadinnhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadInnhold
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdAlder
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.SøknadsinnholdUføre
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
sealed interface SøknadsinnholdJson {
    val personopplysninger: PersonopplysningerJson
    val boforhold: BoforholdJson
    val utenlandsopphold: UtenlandsoppholdJson
    val oppholdstillatelse: OppholdstillatelseJson
    val inntektOgPensjon: InntektOgPensjonJson
    val formue: FormueJson
    val forNav: ForNavJson
    val ektefelle: EktefelleJson?

    fun toSøknadsinnhold() = when (this) {
        is SøknadsinnholdAlderJson -> toSøknadsinnholdAlder()
        is SøknadsinnholdUføreJson -> toSøknadsinnholdUføre()
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
) : SøknadsinnholdJson {

    fun toSøknadsinnholdAlder(): Either<KunneIkkeLageSøknadinnhold, SøknadsinnholdAlder> =
        SøknadsinnholdAlder.tryCreate(
            harSøktAlderspensjon = harSøktAlderspensjon.toHarSøktAlderspensjon(),
            oppholdstillatelseAlder = oppholdstillatelseAlder.toOppholdstillatelseAlder(),
            personopplysninger = personopplysninger.toPersonopplysninger(),
            boforhold = boforhold.toBoforhold().getOrElse {
                return KunneIkkeLageSøknadinnhold.FeilVedOpprettelseAvBoforholdWeb(it).left()
            },
            utenlandsopphold = utenlandsopphold.toUtenlandsopphold(),
            oppholdstillatelse = oppholdstillatelse.toOppholdstillatelse().getOrElse {
                return KunneIkkeLageSøknadinnhold.FeilVedOpprettelseAvOppholdstillatelseWeb(it).left()
            },
            inntektOgPensjon = inntektOgPensjon.toInntektOgPensjon(),
            formue = formue.toFormue().getOrElse {
                return KunneIkkeLageSøknadinnhold.FeilVedOpprettelseAvFormueWeb(it).left()
            },
            forNav = forNav.toForNav(),
            ektefelle = ektefelle?.toEktefelle()?.getOrElse {
                return KunneIkkeLageSøknadinnhold.FeilVedOpprettelseAvEktefelleWeb(it).left()
            },
        ).getOrElse {
            return KunneIkkeLageSøknadinnhold.FeilVedOpprettelseAvSøknadinnholdWeb(it).left()
        }.right()

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
) : SøknadsinnholdJson {

    fun toSøknadsinnholdUføre(): Either<KunneIkkeLageSøknadinnhold, SøknadsinnholdUføre> =
        SøknadsinnholdUføre.tryCreate(
            uførevedtak = uførevedtak.toUførevedtak(),
            personopplysninger = personopplysninger.toPersonopplysninger(),
            flyktningsstatus = flyktningsstatus.toFlyktningsstatus(),
            boforhold = boforhold.toBoforhold().getOrElse {
                return KunneIkkeLageSøknadinnhold.FeilVedOpprettelseAvBoforholdWeb(it).left()
            },
            utenlandsopphold = utenlandsopphold.toUtenlandsopphold(),
            oppholdstillatelse = oppholdstillatelse.toOppholdstillatelse().getOrElse {
                return KunneIkkeLageSøknadinnhold.FeilVedOpprettelseAvOppholdstillatelseWeb(it).left()
            },
            inntektOgPensjon = inntektOgPensjon.toInntektOgPensjon(),
            formue = formue.toFormue().getOrElse {
                return KunneIkkeLageSøknadinnhold.FeilVedOpprettelseAvFormueWeb(it).left()
            },
            forNav = forNav.toForNav(),
            ektefelle = ektefelle?.toEktefelle()?.getOrElse {
                return KunneIkkeLageSøknadinnhold.FeilVedOpprettelseAvEktefelleWeb(it).left()
            },
        ).getOrElse {
            return KunneIkkeLageSøknadinnhold.FeilVedOpprettelseAvSøknadinnholdWeb(it).left()
        }.right()

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

sealed interface KunneIkkeLageSøknadinnhold {
    data class FeilVedOpprettelseAvOppholdstillatelseWeb(val underliggendeFeil: FeilVedOpprettelseAvOppholdstillatelse) :
        KunneIkkeLageSøknadinnhold

    data class FeilVedOpprettelseAvBoforholdWeb(val underliggendeFeil: FeilVedOpprettelseAvBoforhold) :
        KunneIkkeLageSøknadinnhold

    data class FeilVedOpprettelseAvFormueWeb(val underliggendeFeil: FeilVedOpprettelseAvFormue) :
        KunneIkkeLageSøknadinnhold

    data class FeilVedOpprettelseAvEktefelleWeb(val underliggendeFeil: FeilVedOpprettelseAvEktefelleJson) :
        KunneIkkeLageSøknadinnhold

    data class FeilVedOpprettelseAvSøknadinnholdWeb(val underliggendeFeil: FeilVedOpprettelseAvSøknadinnhold) :
        KunneIkkeLageSøknadinnhold
}
