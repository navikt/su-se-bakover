package no.nav.su.se.bakover.domain

import java.time.LocalDate

data class SøknadInnhold(
    val uførevedtak: Uførevedtak,
    val personopplysninger: Person,
    val flyktningsstatus: Flyktningsstatus,
    val boforhold: Boforhold,
    val utenlandsopphold: Utenlandsopphold,
    val oppholdstillatelse: Oppholdstillatelse,
    val inntektOgPensjon: InntektOgPensjon,
    val formue: Formue,
    val forNav: ForNav
)

data class Uførevedtak(
    val harUførevedtak: Boolean
)

data class Flyktningsstatus(
    val registrertFlyktning: Boolean
)

data class Oppholdstillatelse(
    val erNorskStatsborger: Boolean,
    val harOppholdstillatelse: Boolean? = null,
    val oppholdstillatelseType: OppholdstillatelseType? = null,
    val oppholdstillatelseMindreEnnTreMåneder: Boolean? = null,
    val oppholdstillatelseForlengelse: Boolean? = null,
    val statsborgerskapAndreLand: Boolean,
    val statsborgerskapAndreLandFritekst: String? = null
) {
    enum class OppholdstillatelseType() {
        MIDLERTIG,
        PERMANENT;
    }
}

data class Boforhold(
    val borOgOppholderSegINorge: Boolean,
    val delerBolig: Boolean,
    val delerBoligMed: DelerBoligMed? = null,
    val ektemakeEllerSamboerUnder67År: Boolean? = null,
    val ektemakeEllerSamboerUførFlyktning: Boolean? = null

) {
    enum class DelerBoligMed() {
        EKTEMAKE_SAMBOER,
        VOKSNE_BARN,
        ANNEN_VOKSEN;
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

data class ForNav(
    val harFullmektigEllerVerge: Vergemål? = null
) {
    enum class Vergemål() {
        FULLMEKTIG,
        VERGE;
    }
}

data class InntektOgPensjon(
    val forventetInntekt: Number? = null,
    val tjenerPengerIUtlandetBeløp: Number? = null,
    val andreYtelserINav: String? = null,
    val andreYtelserINavBeløp: Number? = null,
    val søktAndreYtelserIkkeBehandletBegrunnelse: String? = null,
    val sosialstønadBeløp: Number? = null,
    val trygdeytelseIUtlandet: List<TrygdeytelseIUtlandet>? = null,
    val pensjon: List<PensjonsOrdningBeløp>? = null
)

data class Formue(
    val borIBolig: Boolean? = null,
    val verdiPåBolig: Number? = null,
    val boligBrukesTil: String? = null,
    val depositumsBeløp: Number? = null,
    val kontonummer: String? = null,
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
    val fra: String
)
