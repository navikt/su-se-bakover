package no.nav.su.se.bakover.web.routes.json

import no.nav.su.meldinger.kafka.soknad.Boforhold
import no.nav.su.meldinger.kafka.soknad.Flyktningsstatus
import no.nav.su.meldinger.kafka.soknad.ForNav
import no.nav.su.meldinger.kafka.soknad.Formue
import no.nav.su.meldinger.kafka.soknad.InntektOgPensjon
import no.nav.su.meldinger.kafka.soknad.Oppholdstillatelse
import no.nav.su.meldinger.kafka.soknad.PensjonsOrdningBeløp
import no.nav.su.meldinger.kafka.soknad.Personopplysninger
import no.nav.su.meldinger.kafka.soknad.SøknadInnhold
import no.nav.su.meldinger.kafka.soknad.Uførevedtak
import no.nav.su.meldinger.kafka.soknad.Utenlandsopphold
import no.nav.su.meldinger.kafka.soknad.UtenlandsoppholdPeriode
import no.nav.su.se.bakover.web.routes.json.SøknadInnholdJson.BoforholdJson.Companion.toBoforholdJson
import no.nav.su.se.bakover.web.routes.json.SøknadInnholdJson.FlyktningsstatusJson.Companion.toFlyktningsstatusJson
import no.nav.su.se.bakover.web.routes.json.SøknadInnholdJson.ForNavJson.Companion.toForNavJson
import no.nav.su.se.bakover.web.routes.json.SøknadInnholdJson.FormueJson.Companion.toFormueJson
import no.nav.su.se.bakover.web.routes.json.SøknadInnholdJson.InntektOgPensjonJson.Companion.toInntektOgPensjonJson
import no.nav.su.se.bakover.web.routes.json.SøknadInnholdJson.OppholdstillatelseJson.Companion.toOppholdstillatelseJson
import no.nav.su.se.bakover.web.routes.json.SøknadInnholdJson.PensjonsOrdningBeløpJson.Companion.toPensjonsOrdningBeløpJson
import no.nav.su.se.bakover.web.routes.json.SøknadInnholdJson.PersonopplysningerJson.Companion.toPersonopplysningerJson
import no.nav.su.se.bakover.web.routes.json.SøknadInnholdJson.UførevedtakJson.Companion.toUførevedtakJson
import no.nav.su.se.bakover.web.routes.json.SøknadInnholdJson.UtenlandsoppholdJson.Companion.toUtenlandsoppholdJson
import no.nav.su.se.bakover.web.routes.json.SøknadInnholdJson.UtenlandsoppholdPeriodeJson.Companion.toUtenlandsoppholdJson
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class SøknadInnholdJson(
    val uførevedtak: UførevedtakJson,
    val personopplysninger: PersonopplysningerJson,
    val flyktningsstatus: FlyktningsstatusJson,
    val boforhold: BoforholdJson,
    val utenlandsopphold: UtenlandsoppholdJson,
    val oppholdstillatelse: OppholdstillatelseJson,
    val inntektOgPensjon: InntektOgPensjonJson,
    val formue: FormueJson,
    val forNav: ForNavJson
) {

    data class UførevedtakJson(
        val harUførevedtak: Boolean
    ) {
        fun toUførevedtak() = Uførevedtak(harUførevedtak)

        companion object {
            fun Uførevedtak.toUførevedtakJson() = UførevedtakJson(this.harUførevedtak)
        }
    }

    data class FlyktningsstatusJson(
        val registrertFlyktning: Boolean
    ) {
        fun toFlyktningsstatus() = Flyktningsstatus(registrertFlyktning)

        companion object {
            fun Flyktningsstatus.toFlyktningsstatusJson() = FlyktningsstatusJson(this.registrertFlyktning)
        }
    }

    data class PersonopplysningerJson(
        val fnr: String,
        val fornavn: String,
        val mellomnavn: String? = null,
        val etternavn: String,
        val telefonnummer: String,
        val gateadresse: String,
        val postnummer: String,
        val poststed: String,
        val bruksenhet: String? = null,
        val bokommune: String,
        val statsborgerskap: String
    ) {
        fun toPersonopplysninger() = Personopplysninger(
            fnr = fnr,
            fornavn = fornavn,
            mellomnavn = mellomnavn,
            etternavn = etternavn,
            telefonnummer = telefonnummer,
            gateadresse = gateadresse,
            postnummer = postnummer,
            poststed = poststed,
            bruksenhet = bruksenhet,
            bokommune = bokommune,
            statsborgerskap = statsborgerskap
        )

        companion object {
            fun Personopplysninger.toPersonopplysningerJson() = PersonopplysningerJson(
                fnr = this.fnr,
                fornavn = this.fornavn,
                mellomnavn = this.mellomnavn,
                etternavn = this.etternavn,
                telefonnummer = this.telefonnummer,
                gateadresse = this.gateadresse,
                postnummer = this.postnummer,
                poststed = this.poststed,
                bruksenhet = this.bruksenhet,
                bokommune = this.bokommune,
                statsborgerskap = this.statsborgerskap
            )
        }
    }

    data class OppholdstillatelseJson(
        val erNorskStatsborger: Boolean,
        val harOppholdstillatelse: Boolean? = null,
        val typeOppholdstillatelse: String? = null,
        val oppholdstillatelseMindreEnnTreMåneder: Boolean? = null,
        val oppholdstillatelseForlengelse: Boolean? = null,
        val statsborgerskapAndreLand: Boolean,
        val statsborgerskapAndreLandFritekst: String? = null
    ) {
        fun toOppholdstillatelse() = Oppholdstillatelse(
            erNorskStatsborger = erNorskStatsborger,
            harOppholdstillatelse = harOppholdstillatelse,
            oppholdstillatelseType = typeOppholdstillatelse?.let {
                Oppholdstillatelse.OppholdstillatelseType.fromString(typeOppholdstillatelse)
            },
            oppholdstillatelseMindreEnnTreMåneder = oppholdstillatelseMindreEnnTreMåneder,
            oppholdstillatelseForlengelse = oppholdstillatelseForlengelse,
            statsborgerskapAndreLand = statsborgerskapAndreLand,
            statsborgerskapAndreLandFritekst = statsborgerskapAndreLandFritekst
        )

        companion object {
            fun Oppholdstillatelse.toOppholdstillatelseJson() = OppholdstillatelseJson(
                erNorskStatsborger = this.erNorskStatsborger,
                harOppholdstillatelse = this.harOppholdstillatelse,
                typeOppholdstillatelse = this.oppholdstillatelseType?.value,
                oppholdstillatelseMindreEnnTreMåneder = this.oppholdstillatelseMindreEnnTreMåneder,
                oppholdstillatelseForlengelse = this.oppholdstillatelseForlengelse,
                statsborgerskapAndreLand = this.statsborgerskapAndreLand,
                statsborgerskapAndreLandFritekst = this.statsborgerskapAndreLandFritekst
            )
        }
    }

    data class BoforholdJson(
        val borOgOppholderSegINorge: Boolean,
        val delerBolig: Boolean,
        val delerBoligMed: String? = null,
        val ektemakeEllerSamboerUnder67År: Boolean? = null,
        val ektemakeEllerSamboerUførFlyktning: Boolean? = null
    ) {
        fun toBoforhold() = Boforhold(
            borOgOppholderSegINorge = borOgOppholderSegINorge,
            delerBolig = delerBolig,
            delerBoligMed = delerBoligMed?.let {
                Boforhold.DelerBoligMed.fromString(it)
            },
            ektemakeEllerSamboerUnder67År = ektemakeEllerSamboerUnder67År,
            ektemakeEllerSamboerUførFlyktning = ektemakeEllerSamboerUførFlyktning
        )

        companion object {
            fun Boforhold.toBoforholdJson() = BoforholdJson(
                borOgOppholderSegINorge = this.borOgOppholderSegINorge,
                delerBolig = this.delerBolig,
                delerBoligMed = this.delerBoligMed?.value,
                ektemakeEllerSamboerUnder67År = this.ektemakeEllerSamboerUnder67År,
                ektemakeEllerSamboerUførFlyktning = this.ektemakeEllerSamboerUførFlyktning
            )
        }
    }

    data class UtenlandsoppholdJson(
        val registrertePerioder: List<UtenlandsoppholdPeriodeJson>? = null, // TODO hestad: Set lists to emptyList
        val planlagtePerioder: List<UtenlandsoppholdPeriodeJson>? = null
    ) {
        fun toUtenlandsopphold() = Utenlandsopphold(
            registrertePerioder = registrertePerioder.toJson(),
            planlagtePerioder = planlagtePerioder.toJson()
        )

        fun List<UtenlandsoppholdPeriodeJson>?.toJson() = this?.map { it.toUtenlandsopphold() }

        companion object {

            fun Utenlandsopphold.toUtenlandsoppholdJson() = UtenlandsoppholdJson(
                registrertePerioder = this.registrertePerioder.toUtenlandsoppholdPeriodeJsonList(),
                planlagtePerioder = this.planlagtePerioder.toUtenlandsoppholdPeriodeJsonList()
            )

            fun List<UtenlandsoppholdPeriode>?.toUtenlandsoppholdPeriodeJsonList() =
                this?.map { it.toUtenlandsoppholdJson() }
        }
    }

    data class UtenlandsoppholdPeriodeJson(
        val utreisedato: String,
        val innreisedato: String
    ) {
        fun toUtenlandsopphold() = UtenlandsoppholdPeriode(
            utreisedato = LocalDate.parse(
                utreisedato,
                DateTimeFormatter.ISO_DATE
            ),
            innreisedato = LocalDate.parse(
                innreisedato,
                DateTimeFormatter.ISO_DATE
            )
        )

        companion object {
            fun UtenlandsoppholdPeriode.toUtenlandsoppholdJson() = UtenlandsoppholdPeriodeJson(
                utreisedato = utreisedato.toString(),
                innreisedato = innreisedato.toString()
            )
        }
    }

    data class ForNavJson(
        val harFullmektigEllerVerge: String? = null
    ) {
        fun toForNav() = ForNav(harFullmektigEllerVerge?.let {
            ForNav.Vergemål.fromString(it)
        })

        companion object {
            fun ForNav.toForNavJson() = ForNavJson(this.harFullmektigEllerVerge?.value)
        }
    }

    data class InntektOgPensjonJson(
        val forventetInntekt: Number? = null,
        val tjenerPengerIUtlandetBeløp: Number? = null,
        val andreYtelserINav: String? = null,
        val andreYtelserINavBeløp: Number? = null,
        val søktAndreYtelserIkkeBehandletBegrunnelse: String? = null,
        val sosialstønadBeløp: Number? = null,
        val trygdeytelserIUtlandetBeløp: Number? = null,
        val trygdeytelserIUtlandet: String? = null,
        val trygdeytelserIUtlandetFra: String? = null,
        val pensjon: List<PensjonsOrdningBeløpJson>? = null
    ) {
        fun toInntektOgPensjon() = InntektOgPensjon(
            forventetInntekt = forventetInntekt,
            tjenerPengerIUtlandetBeløp = tjenerPengerIUtlandetBeløp,
            andreYtelserINav = andreYtelserINav,
            andreYtelserINavBeløp = andreYtelserINavBeløp,
            søktAndreYtelserIkkeBehandletBegrunnelse = søktAndreYtelserIkkeBehandletBegrunnelse,
            sosialstønadBeløp = sosialstønadBeløp,
            trygdeytelserIUtlandetBeløp = trygdeytelserIUtlandetBeløp,
            trygdeytelserIUtlandet = trygdeytelserIUtlandet,
            trygdeytelserIUtlandetFra = trygdeytelserIUtlandetFra,
            pensjon = pensjon.toPensjonList()
        )

        fun List<PensjonsOrdningBeløpJson>?.toPensjonList() = this?.map {
            it.toPensjonsOrdningBeløp()
        }

        companion object {
            fun InntektOgPensjon.toInntektOgPensjonJson() = InntektOgPensjonJson(
                forventetInntekt = forventetInntekt,
                tjenerPengerIUtlandetBeløp = tjenerPengerIUtlandetBeløp,
                andreYtelserINav = andreYtelserINav,
                andreYtelserINavBeløp = andreYtelserINavBeløp,
                søktAndreYtelserIkkeBehandletBegrunnelse = søktAndreYtelserIkkeBehandletBegrunnelse,
                sosialstønadBeløp = sosialstønadBeløp,
                trygdeytelserIUtlandetBeløp = trygdeytelserIUtlandetBeløp,
                trygdeytelserIUtlandet = trygdeytelserIUtlandet,
                trygdeytelserIUtlandetFra = trygdeytelserIUtlandetFra,
                pensjon = pensjon.toPensjonsOrdningBeløpListJson()
            )

            fun List<PensjonsOrdningBeløp>?.toPensjonsOrdningBeløpListJson() = this?.map {
                it.toPensjonsOrdningBeløpJson()
            }
        }
    }

    data class FormueJson(
        val borIBolig: Boolean? = null,
        val verdiPåBolig: Number? = null,
        val boligBrukesTil: String? = null,
        val depositumsBeløp: Number? = null,
        val kontonummer: String? = null,
        val verdiPåEiendom: Number? = null,
        val eiendomBrukesTil: String? = null,
        val verdiPåKjøretøy: Number? = null,
        val kjøretøyDeEier: String? = null,
        val innskuddsBeløp: Number? = null,
        val verdipapirBeløp: Number? = null,
        val skylderNoenMegPengerBeløp: Number? = null,
        val kontanterBeløp: Number? = null
    ) {
        fun toFormue() = Formue(
            borIBolig = borIBolig,
            verdiPåBolig = verdiPåBolig,
            boligBrukesTil = boligBrukesTil,
            depositumsBeløp = depositumsBeløp,
            Kontonummer = kontonummer,
            verdiPåEiendom = verdiPåEiendom,
            eiendomBrukesTil = eiendomBrukesTil,
            verdiPåKjøretøy = verdiPåKjøretøy,
            kjøretøyDeEier = kjøretøyDeEier,
            innskuddsBeløp = innskuddsBeløp,
            verdipapirBeløp = verdipapirBeløp,
            skylderNoenMegPengerBeløp = skylderNoenMegPengerBeløp,
            kontanterBeløp = kontanterBeløp
        )

        companion object {
            fun Formue.toFormueJson() = FormueJson(
                borIBolig = borIBolig,
                verdiPåBolig = verdiPåBolig,
                boligBrukesTil = boligBrukesTil,
                depositumsBeløp = depositumsBeløp,
                kontonummer = Kontonummer,
                verdiPåEiendom = verdiPåEiendom,
                eiendomBrukesTil = eiendomBrukesTil,
                verdiPåKjøretøy = verdiPåKjøretøy,
                kjøretøyDeEier = kjøretøyDeEier,
                innskuddsBeløp = innskuddsBeløp,
                verdipapirBeløp = verdipapirBeløp,
                skylderNoenMegPengerBeløp = skylderNoenMegPengerBeløp,
                kontanterBeløp = kontanterBeløp
            )
        }
    }

    data class PensjonsOrdningBeløpJson(
        val ordning: String,
        val beløp: Double
    ) {
        fun toPensjonsOrdningBeløp() = PensjonsOrdningBeløp(
            ordning = ordning,
            beløp = beløp
        )

        companion object {
            fun PensjonsOrdningBeløp.toPensjonsOrdningBeløpJson() = PensjonsOrdningBeløpJson(
                ordning = ordning,
                beløp = beløp
            )
        }
    }

    fun toSøknadInnhold() = SøknadInnhold(
        uførevedtak = uførevedtak.toUførevedtak(),
        personopplysninger = personopplysninger.toPersonopplysninger(),
        flyktningsstatus = flyktningsstatus.toFlyktningsstatus(),
        boforhold = boforhold.toBoforhold(),
        utenlandsopphold = utenlandsopphold.toUtenlandsopphold(),
        oppholdstillatelse = oppholdstillatelse.toOppholdstillatelse(),
        inntektOgPensjon = inntektOgPensjon.toInntektOgPensjon(),
        formue = formue.toFormue(),
        forNav = forNav.toForNav()
    )

    companion object {
        fun SøknadInnhold.toSøknadInnholdJson() = SøknadInnholdJson(
            uførevedtak = uførevedtak.toUførevedtakJson(),
            personopplysninger = personopplysninger.toPersonopplysningerJson(),
            flyktningsstatus = flyktningsstatus.toFlyktningsstatusJson(),
            boforhold = boforhold.toBoforholdJson(),
            utenlandsopphold = utenlandsopphold.toUtenlandsoppholdJson(),
            oppholdstillatelse = oppholdstillatelse.toOppholdstillatelseJson(),
            inntektOgPensjon = inntektOgPensjon.toInntektOgPensjonJson(),
            formue = formue.toFormueJson(),
            forNav = forNav.toForNavJson()
        )
    }
}
