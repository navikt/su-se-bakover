package no.nav.su.se.bakover.web.routes.søknad

import no.nav.su.se.bakover.domain.Boforhold
import no.nav.su.se.bakover.domain.Boforhold.DelerBoligMed
import no.nav.su.se.bakover.domain.Boforhold.EktefellePartnerSamboer
import no.nav.su.se.bakover.domain.Ektefelle
import no.nav.su.se.bakover.domain.Flyktningsstatus
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.ForNav
import no.nav.su.se.bakover.domain.Formue
import no.nav.su.se.bakover.domain.InntektOgPensjon
import no.nav.su.se.bakover.domain.Kjøretøy
import no.nav.su.se.bakover.domain.Oppholdstillatelse
import no.nav.su.se.bakover.domain.PensjonsOrdningBeløp
import no.nav.su.se.bakover.domain.Personopplysninger
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.domain.TrygdeytelseIUtlandet
import no.nav.su.se.bakover.domain.Uførevedtak
import no.nav.su.se.bakover.domain.Utenlandsopphold
import no.nav.su.se.bakover.domain.UtenlandsoppholdPeriode
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.BoforholdJson.Companion.toBoforholdJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.EktefelleJson.Companion.toJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.FlyktningsstatusJson.Companion.toFlyktningsstatusJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.ForNavJson.Companion.toForNavJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.FormueJson.Companion.toFormueJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.InntektOgPensjonJson.Companion.toInntektOgPensjonJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.KjøretøyJson.Companion.toKjøretøyJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.OppholdstillatelseJson.Companion.toOppholdstillatelseJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.PensjonsOrdningBeløpJson.Companion.toPensjonsOrdningBeløpJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.PersonopplysningerJson.Companion.toPersonopplysningerJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.TrygdeytelserIUtlandetJson.Companion.toTrygdeytelseIUtlandetJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.UførevedtakJson.Companion.toUførevedtakJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.UtenlandsoppholdJson.Companion.toUtenlandsoppholdJson
import no.nav.su.se.bakover.web.routes.søknad.SøknadInnholdJson.UtenlandsoppholdPeriodeJson.Companion.toUtenlandsoppholdJson
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
    val forNav: ForNavJson,
    val ektefelle: EktefelleJson?,
) {

    data class UførevedtakJson(
        val harUførevedtak: Boolean
    ) {
        fun toUførevedtak() = Uførevedtak(harUførevedtak)

        companion object {
            fun Uførevedtak.toUførevedtakJson() =
                UførevedtakJson(this.harUførevedtak)
        }
    }

    data class FlyktningsstatusJson(
        val registrertFlyktning: Boolean
    ) {
        fun toFlyktningsstatus() = Flyktningsstatus(registrertFlyktning)

        companion object {
            fun Flyktningsstatus.toFlyktningsstatusJson() =
                FlyktningsstatusJson(this.registrertFlyktning)
        }
    }

    data class PersonopplysningerJson(
        val fnr: String
    ) {
        fun toPersonopplysninger() = Personopplysninger(
            fnr = Fnr(fnr)
        )

        companion object {
            fun Personopplysninger.toPersonopplysningerJson() =
                PersonopplysningerJson(
                    fnr = this.fnr.toString()
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
                toOppholdstillatelseType(it)
            },
            oppholdstillatelseMindreEnnTreMåneder = oppholdstillatelseMindreEnnTreMåneder,
            oppholdstillatelseForlengelse = oppholdstillatelseForlengelse,
            statsborgerskapAndreLand = statsborgerskapAndreLand,
            statsborgerskapAndreLandFritekst = statsborgerskapAndreLandFritekst
        )

        private fun toOppholdstillatelseType(str: String): Oppholdstillatelse.OppholdstillatelseType {
            return when (str) {
                "midlertidig" -> Oppholdstillatelse.OppholdstillatelseType.MIDLERTIG
                "permanent" -> Oppholdstillatelse.OppholdstillatelseType.PERMANENT
                else -> throw IllegalArgumentException("Ikke gyldig oppholdstillatelse type")
            }
        }

        companion object {
            fun Oppholdstillatelse.toOppholdstillatelseJson() =
                OppholdstillatelseJson(
                    erNorskStatsborger = this.erNorskStatsborger,
                    harOppholdstillatelse = this.harOppholdstillatelse,
                    typeOppholdstillatelse = this.oppholdstillatelseType?.toJson(),
                    oppholdstillatelseMindreEnnTreMåneder = this.oppholdstillatelseMindreEnnTreMåneder,
                    oppholdstillatelseForlengelse = this.oppholdstillatelseForlengelse,
                    statsborgerskapAndreLand = this.statsborgerskapAndreLand,
                    statsborgerskapAndreLandFritekst = this.statsborgerskapAndreLandFritekst
                )
        }
    }

    data class BoforholdJson(
        val borOgOppholderSegINorge: Boolean,
        val delerBoligMedVoksne: Boolean,
        val delerBoligMed: String? = null,
        val ektefellePartnerSamboer: EktefellePartnerSamboer?,
    ) {
        fun toBoforhold() = Boforhold(
            borOgOppholderSegINorge = borOgOppholderSegINorge,
            delerBolig = delerBoligMedVoksne,
            delerBoligMed = delerBoligMed?.let {
                toBoforholdType(it)
            },
            ektefellePartnerSamboer = ektefellePartnerSamboer
        )

        private fun toBoforholdType(str: String): DelerBoligMed {
            return when (str) {
                "EKTEMAKE_SAMBOER" -> DelerBoligMed.EKTEMAKE_SAMBOER
                "VOKSNE_BARN" -> DelerBoligMed.VOKSNE_BARN
                "ANNEN_VOKSEN" -> DelerBoligMed.ANNEN_VOKSEN
                else -> throw IllegalArgumentException("delerBoligMed feltet er ugyldig")
            }
        }

        companion object {
            fun Boforhold.toBoforholdJson() =
                BoforholdJson(
                    borOgOppholderSegINorge = this.borOgOppholderSegINorge,
                    delerBoligMedVoksne = this.delerBolig,
                    delerBoligMed = this.delerBoligMed?.toJson(),
                    ektefellePartnerSamboer = this.ektefellePartnerSamboer,
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

            fun Utenlandsopphold.toUtenlandsoppholdJson() =
                UtenlandsoppholdJson(
                    registrertePerioder = this.registrertePerioder.toUtenlandsoppholdPeriodeJsonList(),
                    planlagtePerioder = this.planlagtePerioder.toUtenlandsoppholdPeriodeJsonList()
                )

            fun List<UtenlandsoppholdPeriode>?.toUtenlandsoppholdPeriodeJsonList(): List<UtenlandsoppholdPeriodeJson>? =
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
            fun UtenlandsoppholdPeriode.toUtenlandsoppholdJson() =
                UtenlandsoppholdPeriodeJson(
                    utreisedato = utreisedato.toString(),
                    innreisedato = innreisedato.toString()
                )
        }
    }

    data class ForNavJson(
        val harFullmektigEllerVerge: String? = null
    ) {
        fun toForNav() = ForNav(
            harFullmektigEllerVerge?.let {
                vergeMålType(it)
            }
        )

        private fun vergeMålType(str: String): ForNav.Vergemål {
            return when (str) {
                "fullmektig" -> ForNav.Vergemål.FULLMEKTIG
                "verge" -> ForNav.Vergemål.VERGE
                else -> throw IllegalArgumentException("Vergemål er ugyldig")
            }
        }

        companion object {
            fun ForNav.toForNavJson() =
                ForNavJson(this.harFullmektigEllerVerge?.toJson())
        }
    }

    data class EktefelleJson(val formue: FormueJson, val inntektOgPensjon: InntektOgPensjonJson) {
        fun toEktefelle() = Ektefelle(
            formue = formue.toFormue(),
            inntektOgPensjon = inntektOgPensjon.toInntektOgPensjon()
        )

        companion object {
            fun Ektefelle.toJson() = EktefelleJson(
                formue = formue.toFormueJson(),
                inntektOgPensjon = inntektOgPensjon.toInntektOgPensjonJson()
            )
        }
    }

    data class InntektOgPensjonJson(
        val forventetInntekt: Number? = null,
        val andreYtelserINav: String? = null,
        val andreYtelserINavBeløp: Number? = null,
        val søktAndreYtelserIkkeBehandletBegrunnelse: String? = null,
        val sosialstønadBeløp: Number? = null,
        val trygdeytelserIUtlandet: List<TrygdeytelserIUtlandetJson>? = null,
        val pensjon: List<PensjonsOrdningBeløpJson>? = null
    ) {
        fun toInntektOgPensjon() = InntektOgPensjon(
            forventetInntekt = forventetInntekt,
            andreYtelserINav = andreYtelserINav,
            andreYtelserINavBeløp = andreYtelserINavBeløp,
            søktAndreYtelserIkkeBehandletBegrunnelse = søktAndreYtelserIkkeBehandletBegrunnelse,
            sosialstønadBeløp = sosialstønadBeløp,
            trygdeytelseIUtlandet = trygdeytelserIUtlandet.toTrygdeytelseList(),
            pensjon = pensjon.toPensjonList()
        )

        fun List<PensjonsOrdningBeløpJson>?.toPensjonList() = this?.map {
            it.toPensjonsOrdningBeløp()
        }

        fun List<TrygdeytelserIUtlandetJson>?.toTrygdeytelseList() = this?.map {
            it.toTrygdeytelseIUtlandet()
        }

        companion object {
            fun InntektOgPensjon.toInntektOgPensjonJson() =
                InntektOgPensjonJson(
                    forventetInntekt = forventetInntekt,
                    andreYtelserINav = andreYtelserINav,
                    andreYtelserINavBeløp = andreYtelserINavBeløp,
                    søktAndreYtelserIkkeBehandletBegrunnelse = søktAndreYtelserIkkeBehandletBegrunnelse,
                    sosialstønadBeløp = sosialstønadBeløp,
                    trygdeytelserIUtlandet = trygdeytelseIUtlandet.toTrygdeytelseIUtlandetJson(),
                    pensjon = pensjon.toPensjonsOrdningBeløpListJson()
                )

            fun List<PensjonsOrdningBeløp>?.toPensjonsOrdningBeløpListJson() = this?.map {
                it.toPensjonsOrdningBeløpJson()
            }
            fun List<TrygdeytelseIUtlandet>?.toTrygdeytelseIUtlandetJson() = this?.map {
                it.toTrygdeytelseIUtlandetJson()
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
        val kjøretøy: List<KjøretøyJson>? = null,
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
            kontonummer = kontonummer,
            verdiPåEiendom = verdiPåEiendom,
            eiendomBrukesTil = eiendomBrukesTil,
            kjøretøy = kjøretøy.toKjøretøyList(),
            innskuddsBeløp = innskuddsBeløp,
            verdipapirBeløp = verdipapirBeløp,
            skylderNoenMegPengerBeløp = skylderNoenMegPengerBeløp,
            kontanterBeløp = kontanterBeløp
        )

        fun List<KjøretøyJson>?.toKjøretøyList() = this?.map {
            it.toKjøretøy()
        }

        companion object {
            fun Formue.toFormueJson() =
                FormueJson(
                    borIBolig = borIBolig,
                    verdiPåBolig = verdiPåBolig,
                    boligBrukesTil = boligBrukesTil,
                    depositumsBeløp = depositumsBeløp,
                    kontonummer = kontonummer,
                    verdiPåEiendom = verdiPåEiendom,
                    eiendomBrukesTil = eiendomBrukesTil,
                    kjøretøy = kjøretøy.toKjøretøyListJson(),
                    innskuddsBeløp = innskuddsBeløp,
                    verdipapirBeløp = verdipapirBeløp,
                    skylderNoenMegPengerBeløp = skylderNoenMegPengerBeløp,
                    kontanterBeløp = kontanterBeløp
                )

            fun List<Kjøretøy>?.toKjøretøyListJson() = this?.map {
                it.toKjøretøyJson()
            }
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
            fun PensjonsOrdningBeløp.toPensjonsOrdningBeløpJson() =
                PensjonsOrdningBeløpJson(
                    ordning = ordning,
                    beløp = beløp
                )
        }
    }

    data class TrygdeytelserIUtlandetJson(
        val beløp: Number,
        val type: String,
        val fra: String
    ) {
        fun toTrygdeytelseIUtlandet() = TrygdeytelseIUtlandet(
            beløp = beløp,
            type = type,
            fra = fra
        )

        companion object {
            fun TrygdeytelseIUtlandet.toTrygdeytelseIUtlandetJson() =
                TrygdeytelserIUtlandetJson(
                    beløp = beløp,
                    type = type,
                    fra = fra
                )
        }
    }

    data class KjøretøyJson(val verdiPåKjøretøy: Number, val kjøretøyDeEier: String) {
        fun toKjøretøy() = Kjøretøy(
            kjøretøyDeEier = kjøretøyDeEier,
            verdiPåKjøretøy = verdiPåKjøretøy
        )

        companion object {
            fun Kjøretøy.toKjøretøyJson() =
                KjøretøyJson(
                    kjøretøyDeEier = kjøretøyDeEier,
                    verdiPåKjøretøy = verdiPåKjøretøy
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
        forNav = forNav.toForNav(),
        ektefelle = ektefelle?.toEktefelle(),
    )

    companion object {
        fun SøknadInnhold.toSøknadInnholdJson() =
            SøknadInnholdJson(
                uførevedtak = uførevedtak.toUførevedtakJson(),
                personopplysninger = personopplysninger.toPersonopplysningerJson(),
                flyktningsstatus = flyktningsstatus.toFlyktningsstatusJson(),
                boforhold = boforhold.toBoforholdJson(),
                utenlandsopphold = utenlandsopphold.toUtenlandsoppholdJson(),
                oppholdstillatelse = oppholdstillatelse.toOppholdstillatelseJson(),
                inntektOgPensjon = inntektOgPensjon.toInntektOgPensjonJson(),
                formue = formue.toFormueJson(),
                forNav = forNav.toForNavJson(),
                ektefelle = ektefelle?.let { it.toJson() }
            )
    }
}

private fun Oppholdstillatelse.OppholdstillatelseType.toJson(): String {
    return when (this) {
        Oppholdstillatelse.OppholdstillatelseType.MIDLERTIG -> "midlertidig"
        Oppholdstillatelse.OppholdstillatelseType.PERMANENT -> "permanent"
    }
}

private fun DelerBoligMed.toJson(): String {
    return when (this) {
        DelerBoligMed.EKTEMAKE_SAMBOER -> "EKTEMAKE_SAMBOER"
        DelerBoligMed.VOKSNE_BARN -> "VOKSNE_BARN"
        DelerBoligMed.ANNEN_VOKSEN -> "ANNEN_VOKSEN"
    }
}

private fun ForNav.Vergemål.toJson(): String {
    return when (this) {
        ForNav.Vergemål.VERGE -> "verge"
        ForNav.Vergemål.FULLMEKTIG -> "fullmektig"
    }
}
