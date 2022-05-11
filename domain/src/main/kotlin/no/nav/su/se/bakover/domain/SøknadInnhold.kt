package no.nav.su.se.bakover.domain

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDate

data class SøknadInnhold(
    val uførevedtak: Uførevedtak,
    val personopplysninger: Personopplysninger,
    val flyktningsstatus: Flyktningsstatus,
    val boforhold: Boforhold,
    val utenlandsopphold: Utenlandsopphold,
    val oppholdstillatelse: Oppholdstillatelse,
    val inntektOgPensjon: InntektOgPensjon,
    val formue: Formue,
    val forNav: ForNav,
    val ektefelle: Ektefelle?,
)

data class Uførevedtak(
    val harUførevedtak: Boolean
)

data class Flyktningsstatus(
    val registrertFlyktning: Boolean
)

data class Personopplysninger(
    val fnr: Fnr
)

data class Oppholdstillatelse(
    val erNorskStatsborger: Boolean,
    val harOppholdstillatelse: Boolean? = null,
    val oppholdstillatelseType: OppholdstillatelseType? = null,
    val statsborgerskapAndreLand: Boolean,
    val statsborgerskapAndreLandFritekst: String? = null
) {
    enum class OppholdstillatelseType() {
        MIDLERTIDIG,
        PERMANENT;
    }
}

data class Boforhold(
    val borOgOppholderSegINorge: Boolean,
    val delerBolig: Boolean,
    val delerBoligMed: DelerBoligMed? = null,
    val ektefellePartnerSamboer: EktefellePartnerSamboer? = null,
    val innlagtPåInstitusjon: InnlagtPåInstitusjon?,
    val oppgittAdresse: OppgittAdresse?,
) {
    enum class DelerBoligMed() {
        EKTEMAKE_SAMBOER, // TODO AI: Skal endres till ektefelle
        VOKSNE_BARN,
        ANNEN_VOKSEN;
    }

    data class EktefellePartnerSamboer(
        val erUførFlyktning: Boolean?,
        val fnr: Fnr
    )

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
    )
    @JsonSubTypes(
        JsonSubTypes.Type(value = OppgittAdresse.BorPåAdresse::class, name = "BorPåAdresse"),
        JsonSubTypes.Type(value = OppgittAdresse.IngenAdresse::class, name = "IngenAdresse"),
    )
    sealed class OppgittAdresse {
        data class BorPåAdresse(
            val adresselinje: String,
            val postnummer: String,
            val poststed: String?,
            val bruksenhet: String?
        ) : OppgittAdresse() {
            override fun toString() = "${hentGateAdresse()}, ${hentPostAdresse()}"

            private fun hentGateAdresse() = if (bruksenhet != null) "$adresselinje $bruksenhet" else adresselinje
            private fun hentPostAdresse() = if (poststed != null) "$postnummer $poststed" else postnummer
        }

        data class IngenAdresse(
            val grunn: IngenAdresseGrunn
        ) : OppgittAdresse() {

            enum class IngenAdresseGrunn {
                BOR_PÅ_ANNEN_ADRESSE,
                HAR_IKKE_FAST_BOSTED
            }
        }
    }
}

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

data class Formue(
    val eierBolig: Boolean,
    val borIBolig: Boolean? = null,
    val verdiPåBolig: Number? = null,
    val boligBrukesTil: String? = null,
    val depositumsBeløp: Number? = null,
    val verdiPåEiendom: Number? = null,
    val eiendomBrukesTil: String? = null,
    val kjøretøy: List<Kjøretøy>? = null,
    val innskuddsBeløp: Number? = null,
    val verdipapirBeløp: Number? = null,
    val skylderNoenMegPengerBeløp: Number? = null,
    val kontanterBeløp: Number? = null
)

data class PensjonsOrdningBeløp(
    val ordning: String,
    val beløp: Double
)

data class Kjøretøy(
    val verdiPåKjøretøy: Number,
    val kjøretøyDeEier: String
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
