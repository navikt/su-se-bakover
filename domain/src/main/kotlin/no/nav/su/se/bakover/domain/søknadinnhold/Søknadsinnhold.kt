package no.nav.su.se.bakover.domain.søknadinnhold

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sakstype

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

    fun oppdaterFnr(fnr: Fnr) = when (this) {
        is SøknadsinnholdAlder -> this.copy(
            personopplysninger = personopplysninger.copy(fnr = fnr),
        )
        is SøknadsinnholdUføre -> this.copy(
            personopplysninger = personopplysninger.copy(fnr = fnr),
        )
    }

    fun type() = when (this) {
        is SøknadsinnholdAlder -> Sakstype.ALDER
        is SøknadsinnholdUføre -> Sakstype.UFØRE
    }
}

data class SøknadsinnholdAlder(
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
) : SøknadInnhold

data class SøknadsinnholdUføre(
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
) : SøknadInnhold
