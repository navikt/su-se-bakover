package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import arrow.core.Either
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.Flyktningsstatus
import no.nav.su.se.bakover.domain.HarSøktAlderspensjon
import no.nav.su.se.bakover.domain.OppholdstillatelseAlder
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.SøknadsinnholdAlder
import no.nav.su.se.bakover.domain.SøknadsinnholdUføre
import no.nav.su.se.bakover.domain.Uførevedtak
import no.nav.su.se.bakover.domain.søknadinnhold.FeilVedOpprettelseAvOppholdstillatelse
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.BoforholdJson.Companion.toBoforholdJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.EktefelleJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.ForNavJson.Companion.toForNavJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.FormueJson.Companion.toFormueJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.InntektOgPensjonJson.Companion.toInntektOgPensjonJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.OppholdstillatelseJson.Companion.toOppholdstillatelseJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.PersonopplysningerJson.Companion.toPersonopplysningerJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdAlderJson.Companion.toSøknadsinnholdAlderJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdAlderJson.HarSøktAlderspensjonJson.Companion.toHarSøktAlderspensjonJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdAlderJson.OppholdstillatelseAlderJson.Companion.toOppholdstillatelseAlderJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdUføreJson.Companion.toSøknadsinnholdUføreJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdUføreJson.FlyktningsstatusJson.Companion.toFlyktningsstatusJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdUføreJson.UførevedtakJson.Companion.toUførevedtakJson
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

    fun toSøknadsinnholdAlder(): Either<FeilVedOpprettelseAvSøknadinnholdJson, SøknadsinnholdAlder> {
        return SøknadsinnholdAlder(
            harSøktAlderspensjon = harSøktAlderspensjon.toHarSøktAlderspensjon(),
            oppholdstillatelseAlder = oppholdstillatelseAlder.toOppholdstillatelseAlder(),
            personopplysninger = personopplysninger.toPersonopplysninger(),
            boforhold = boforhold.toBoforhold(),
            utenlandsopphold = utenlandsopphold.toUtenlandsopphold(),
            oppholdstillatelse = oppholdstillatelse.toOppholdstillatelse().getOrHandle {
                return FeilVedOpprettelseAvSøknadinnholdJson.FeilVedOpprettelseAvOppholdstillatelseWeb(it).left()
            },
            inntektOgPensjon = inntektOgPensjon.toInntektOgPensjon(),
            formue = formue.toFormue(),
            forNav = forNav.toForNav(),
            ektefelle = ektefelle?.toEktefelle(),
        ).right()
    }

    data class HarSøktAlderspensjonJson(
        val harSøktAlderspensjon: Boolean,
    ) {
        fun toHarSøktAlderspensjon() = HarSøktAlderspensjon(harSøktAlderspensjon)

        companion object {
            fun HarSøktAlderspensjon.toHarSøktAlderspensjonJson() = HarSøktAlderspensjonJson(this.harSøktAlderspensjon)
        }
    }

    data class OppholdstillatelseAlderJson(
        val eøsborger: Boolean?,
        val familieforening: Boolean?,
    ) {
        fun toOppholdstillatelseAlder() =
            OppholdstillatelseAlder(eøsborger = eøsborger, familiegjenforening = familieforening)

        companion object {
            fun OppholdstillatelseAlder.toOppholdstillatelseAlderJson() =
                OppholdstillatelseAlderJson(eøsborger = this.eøsborger, familieforening = this.familiegjenforening)
        }
    }

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

    fun toSøknadsinnholdUføre(): Either<FeilVedOpprettelseAvSøknadinnholdJson, SøknadsinnholdUføre> {
        return SøknadsinnholdUføre(
            uførevedtak = uførevedtak.toUførevedtak(),
            personopplysninger = personopplysninger.toPersonopplysninger(),
            flyktningsstatus = flyktningsstatus.toFlyktningsstatus(),
            boforhold = boforhold.toBoforhold(),
            utenlandsopphold = utenlandsopphold.toUtenlandsopphold(),
            oppholdstillatelse = oppholdstillatelse.toOppholdstillatelse().getOrHandle {
                return FeilVedOpprettelseAvSøknadinnholdJson.FeilVedOpprettelseAvOppholdstillatelseWeb(it).left()
            },
            inntektOgPensjon = inntektOgPensjon.toInntektOgPensjon(),
            formue = formue.toFormue(),
            forNav = forNav.toForNav(),
            ektefelle = ektefelle?.toEktefelle(),
        ).right()
    }

    data class UførevedtakJson(
        val harUførevedtak: Boolean,
    ) {
        fun toUførevedtak() = Uførevedtak(harUførevedtak)

        companion object {
            fun Uførevedtak.toUførevedtakJson() = UførevedtakJson(this.harUførevedtak)
        }
    }

    data class FlyktningsstatusJson(
        val registrertFlyktning: Boolean,
    ) {
        fun toFlyktningsstatus() = Flyktningsstatus(registrertFlyktning)

        companion object {
            fun Flyktningsstatus.toFlyktningsstatusJson() = FlyktningsstatusJson(this.registrertFlyktning)
        }
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

sealed interface FeilVedOpprettelseAvSøknadinnholdJson {
    data class FeilVedOpprettelseAvOppholdstillatelseWeb(val underliggendeFeil: FeilVedOpprettelseAvOppholdstillatelse) :
        FeilVedOpprettelseAvSøknadinnholdJson
}
