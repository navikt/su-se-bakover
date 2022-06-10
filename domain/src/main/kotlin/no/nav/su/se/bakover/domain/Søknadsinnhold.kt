package no.nav.su.se.bakover.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.søknadinnhold.Boforhold
import no.nav.su.se.bakover.domain.søknadinnhold.Formue
import no.nav.su.se.bakover.domain.søknadinnhold.Oppholdstillatelse
import java.time.LocalDate

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

data class Uførevedtak(
    val harUførevedtak: Boolean
)

data class Flyktningsstatus(
    val registrertFlyktning: Boolean
)

data class HarSøktAlderspensjon(
    val harSøktAlderspensjon: Boolean
)

data class Personopplysninger(
    val fnr: Fnr
)

data class OppholdstillatelseAlder(
    val eøsborger: Boolean?,
    val familiegjenforening: Boolean?
)

data class Utenlandsopphold(
    val registrertePerioder: List<UtenlandsoppholdPeriode>? = null,
    val planlagtePerioder: List<UtenlandsoppholdPeriode>? = null
)

data class UtenlandsoppholdPeriode(
    val utreisedato: LocalDate,
    val innreisedato: LocalDate
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = ForNav.DigitalSøknad::class, name = "DigitalSøknad"),
    JsonSubTypes.Type(value = ForNav.Papirsøknad::class, name = "Papirsøknad"),
)
sealed class ForNav {
    data class DigitalSøknad(
        val harFullmektigEllerVerge: Vergemål? = null
    ) : ForNav() {
        enum class Vergemål() {
            FULLMEKTIG,
            VERGE;
        }
    }

    data class Papirsøknad(
        val mottaksdatoForSøknad: LocalDate,
        val grunnForPapirinnsending: GrunnForPapirinnsending,
        val annenGrunn: String?
    ) : ForNav() {
        enum class GrunnForPapirinnsending() {
            VergeHarSøktPåVegneAvBruker,
            MidlertidigUnntakFraOppmøteplikt,
            Annet
        }
    }
}

data class Ektefelle(val formue: Formue, val inntektOgPensjon: InntektOgPensjon)

data class InntektOgPensjon(
    val forventetInntekt: Number? = null,
    val andreYtelserINav: String? = null,
    val andreYtelserINavBeløp: Number? = null,
    val søktAndreYtelserIkkeBehandletBegrunnelse: String? = null,
    val trygdeytelseIUtlandet: List<TrygdeytelseIUtlandet>? = null,
    val pensjon: List<PensjonsOrdningBeløp>? = null
)

data class PensjonsOrdningBeløp(
    val ordning: String,
    val beløp: Double
)

data class TrygdeytelseIUtlandet(
    val beløp: Number,
    val type: String,
    val valuta: String
)

data class InnlagtPåInstitusjon(
    val datoForInnleggelse: LocalDate,
    val datoForUtskrivelse: LocalDate?,
    val fortsattInnlagt: Boolean,
)
