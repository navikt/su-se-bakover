package no.nav.su.se.bakover.web.routes.søknad

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import no.nav.su.se.bakover.domain.Boforhold
import no.nav.su.se.bakover.domain.Boforhold.DelerBoligMed
import no.nav.su.se.bakover.domain.Boforhold.EktefellePartnerSamboer
import no.nav.su.se.bakover.domain.Boforhold.OppgittAdresse.IngenAdresse.IngenAdresseGrunn
import no.nav.su.se.bakover.domain.Ektefelle
import no.nav.su.se.bakover.domain.Flyktningsstatus
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.ForNav
import no.nav.su.se.bakover.domain.Formue
import no.nav.su.se.bakover.domain.InnlagtPåInstitusjon
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
import no.nav.su.se.bakover.web.routes.søknadsbehandling.enumContains
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
        val statsborgerskapAndreLand: Boolean,
        val statsborgerskapAndreLandFritekst: String? = null
    ) {
        fun toOppholdstillatelse() = Oppholdstillatelse(
            erNorskStatsborger = erNorskStatsborger,
            harOppholdstillatelse = harOppholdstillatelse,
            oppholdstillatelseType = typeOppholdstillatelse?.let {
                toOppholdstillatelseType(it)
            },
            statsborgerskapAndreLand = statsborgerskapAndreLand,
            statsborgerskapAndreLandFritekst = statsborgerskapAndreLandFritekst
        )

        private fun toOppholdstillatelseType(str: String): Oppholdstillatelse.OppholdstillatelseType {
            return when (str) {
                "midlertidig" -> Oppholdstillatelse.OppholdstillatelseType.MIDLERTIDIG
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
        val innlagtPåInstitusjon: InnlagtPåInstitusjon?,
        val borPåAdresse: AdresseFraSøknad?,
        val ingenAdresseGrunn: IngenAdresseGrunn?
    ) {
        data class AdresseFraSøknad(
            val adresselinje: String,
            val postnummer: String,
            val poststed: String?,
            val bruksenhet: String?
        )

        fun toBoforhold(): Boforhold {
            if (borPåAdresse != null && ingenAdresseGrunn != null) {
                throw IllegalArgumentException("Ogyldig adresseinformasjon sendt!")
            }

            val oppgittAdresse = when {
                borPåAdresse != null -> Boforhold.OppgittAdresse.BorPåAdresse(
                    adresselinje = borPåAdresse.adresselinje,
                    postnummer = borPåAdresse.postnummer,
                    poststed = borPåAdresse.poststed,
                    bruksenhet = borPåAdresse.bruksenhet
                )
                ingenAdresseGrunn != null -> Boforhold.OppgittAdresse.IngenAdresse(ingenAdresseGrunn)
                else -> null
            }

            return Boforhold(
                borOgOppholderSegINorge = borOgOppholderSegINorge,
                delerBolig = delerBoligMedVoksne,
                delerBoligMed = delerBoligMed?.let {
                    toBoforholdType(it)
                },
                ektefellePartnerSamboer = ektefellePartnerSamboer,
                innlagtPåInstitusjon = innlagtPåInstitusjon,
                oppgittAdresse = oppgittAdresse
            )
        }

        private fun toBoforholdType(str: String): DelerBoligMed {
            return when (str) {
                "EKTEMAKE_SAMBOER" -> DelerBoligMed.EKTEMAKE_SAMBOER
                "VOKSNE_BARN" -> DelerBoligMed.VOKSNE_BARN
                "ANNEN_VOKSEN" -> DelerBoligMed.ANNEN_VOKSEN
                else -> throw IllegalArgumentException("delerBoligMed feltet er ugyldig")
            }
        }

        companion object {
            fun Boforhold.toBoforholdJson(): BoforholdJson {

                return BoforholdJson(
                    borOgOppholderSegINorge = this.borOgOppholderSegINorge,
                    delerBoligMedVoksne = this.delerBolig,
                    delerBoligMed = this.delerBoligMed?.toJson(),
                    ektefellePartnerSamboer = this.ektefellePartnerSamboer,
                    innlagtPåInstitusjon = this.innlagtPåInstitusjon,
                    borPåAdresse = when (val o = this.oppgittAdresse) {
                        is Boforhold.OppgittAdresse.BorPåAdresse -> AdresseFraSøknad(
                            adresselinje = o.adresselinje,
                            postnummer = o.postnummer,
                            poststed = o.poststed,
                            bruksenhet = o.bruksenhet
                        )
                        is Boforhold.OppgittAdresse.IngenAdresse -> null
                        null -> null
                    },
                    ingenAdresseGrunn = when (val o = this.oppgittAdresse) {
                        is Boforhold.OppgittAdresse.BorPåAdresse -> null
                        is Boforhold.OppgittAdresse.IngenAdresse -> o.grunn
                        null -> null
                    }
                )
            }
        }
    }

    data class UtenlandsoppholdJson(
        val registrertePerioder: List<UtenlandsoppholdPeriodeJson>? = null,
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

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
    )
    @JsonSubTypes(
        JsonSubTypes.Type(value = ForNavJson.DigitalSøknad::class, name = "DigitalSøknad"),
        JsonSubTypes.Type(value = ForNavJson.Papirsøknad::class, name = "Papirsøknad"),
    )
    sealed class ForNavJson {
        abstract fun toForNav(): ForNav

        data class DigitalSøknad(
            val harFullmektigEllerVerge: String? = null
        ) : ForNavJson() {
            override fun toForNav() = ForNav.DigitalSøknad(
                harFullmektigEllerVerge?.let {
                    vergeMålType(it)
                }
            )

            private fun vergeMålType(str: String): ForNav.DigitalSøknad.Vergemål {
                return when (str) {
                    "fullmektig" -> ForNav.DigitalSøknad.Vergemål.FULLMEKTIG
                    "verge" -> ForNav.DigitalSøknad.Vergemål.VERGE
                    else -> throw IllegalArgumentException("Vergemål er ugyldig")
                }
            }
        }

        data class Papirsøknad(
            val mottaksdatoForSøknad: LocalDate,
            val grunnForPapirinnsending: String,
            val annenGrunn: String?
        ) : ForNavJson() {
            override fun toForNav() = ForNav.Papirsøknad(
                mottaksdatoForSøknad,
                grunn(grunnForPapirinnsending),
                annenGrunn
            )

            private fun grunn(str: String): ForNav.Papirsøknad.GrunnForPapirinnsending =
                if (enumContains<ForNav.Papirsøknad.GrunnForPapirinnsending>(str)) {
                    ForNav.Papirsøknad.GrunnForPapirinnsending.valueOf(str)
                } else {
                    throw IllegalArgumentException("Ugyldig grunn")
                }
        }

        companion object {
            fun ForNav.toForNavJson() =
                when (this) {
                    is ForNav.DigitalSøknad ->
                        DigitalSøknad(this.harFullmektigEllerVerge?.toJson())
                    is ForNav.Papirsøknad ->
                        Papirsøknad(
                            mottaksdatoForSøknad,
                            grunnForPapirinnsending.toString(),
                            annenGrunn
                        )
                }
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
        val trygdeytelserIUtlandet: List<TrygdeytelserIUtlandetJson>? = null,
        val pensjon: List<PensjonsOrdningBeløpJson>? = null
    ) {
        fun toInntektOgPensjon() = InntektOgPensjon(
            forventetInntekt = forventetInntekt,
            andreYtelserINav = andreYtelserINav,
            andreYtelserINavBeløp = andreYtelserINavBeløp,
            søktAndreYtelserIkkeBehandletBegrunnelse = søktAndreYtelserIkkeBehandletBegrunnelse,
            trygdeytelseIUtlandet = trygdeytelserIUtlandet.toTrygdeytelseList(),
            pensjon = pensjon.toPensjonList()
        )

        private fun List<PensjonsOrdningBeløpJson>?.toPensjonList() = this?.map {
            it.toPensjonsOrdningBeløp()
        }

        private fun List<TrygdeytelserIUtlandetJson>?.toTrygdeytelseList() = this?.map {
            it.toTrygdeytelseIUtlandet()
        }

        companion object {
            fun InntektOgPensjon.toInntektOgPensjonJson() =
                InntektOgPensjonJson(
                    forventetInntekt = forventetInntekt,
                    andreYtelserINav = andreYtelserINav,
                    andreYtelserINavBeløp = andreYtelserINavBeløp,
                    søktAndreYtelserIkkeBehandletBegrunnelse = søktAndreYtelserIkkeBehandletBegrunnelse,
                    trygdeytelserIUtlandet = trygdeytelseIUtlandet.toTrygdeytelseIUtlandetJson(),
                    pensjon = pensjon.toPensjonsOrdningBeløpListJson()
                )

            private fun List<PensjonsOrdningBeløp>?.toPensjonsOrdningBeløpListJson() = this?.map {
                it.toPensjonsOrdningBeløpJson()
            }

            private fun List<TrygdeytelseIUtlandet>?.toTrygdeytelseIUtlandetJson() = this?.map {
                it.toTrygdeytelseIUtlandetJson()
            }
        }
    }

    data class FormueJson(
        val eierBolig: Boolean,
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
            eierBolig = eierBolig,
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

        private fun List<KjøretøyJson>?.toKjøretøyList() = this?.map {
            it.toKjøretøy()
        }

        companion object {
            fun Formue.toFormueJson() =
                FormueJson(
                    eierBolig = eierBolig,
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
        val valuta: String
    ) {
        fun toTrygdeytelseIUtlandet() = TrygdeytelseIUtlandet(
            beløp = beløp,
            type = type,
            valuta = valuta
        )

        companion object {
            fun TrygdeytelseIUtlandet.toTrygdeytelseIUtlandetJson() =
                TrygdeytelserIUtlandetJson(
                    beløp = beløp,
                    type = type,
                    valuta = valuta
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
                ektefelle = ektefelle?.toJson()
            )
    }
}

private fun Oppholdstillatelse.OppholdstillatelseType.toJson(): String {
    return when (this) {
        Oppholdstillatelse.OppholdstillatelseType.MIDLERTIDIG -> "midlertidig"
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

private fun ForNav.DigitalSøknad.Vergemål.toJson(): String {
    return when (this) {
        ForNav.DigitalSøknad.Vergemål.VERGE -> "verge"
        ForNav.DigitalSøknad.Vergemål.FULLMEKTIG -> "fullmektig"
    }
}
