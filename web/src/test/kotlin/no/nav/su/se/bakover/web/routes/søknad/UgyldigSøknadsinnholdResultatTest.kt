package no.nav.su.se.bakover.web.routes.søknad

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.OppgittAdresse
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.BoforholdJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.EktefelleJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.FlyktningsstatusJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.FnrJsonWrapper
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.ForNavJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.FormueJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.InntektOgPensjonJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.KjøretøyJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.OppholdstillatelseJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.PensjonsOrdningBeløpJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdInputValidator
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdUføreJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.TrygdeytelserIUtlandetJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.UførevedtakJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.UgyldigSøknadsinnholdInput
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.UtenlandsoppholdJson
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class UgyldigSøknadsinnholdResultatTest {

    @Test
    fun `returnerer alle inputfeil i errors-listen`() {
        val resultat = listOf(
            UgyldigSøknadsinnholdInput(
                felt = "formue.eiendomBrukesTil",
                begrunnelse = "inneholder mistenkelig innhold",
            ),
            UgyldigSøknadsinnholdInput(
                felt = "inntektOgPensjon.andreYtelserINav",
                begrunnelse = "inneholder kontrolltegn",
            ),
            UgyldigSøknadsinnholdInput(
                felt = "inntektOgPensjon.pensjon.0.ordning",
                begrunnelse = "mangler ordning",
            ),
            UgyldigSøknadsinnholdInput(
                felt = "formue.kjøretøy.0.kjøretøyDeEier",
                begrunnelse = "mangler verdi",
            ),
            UgyldigSøknadsinnholdInput(
                felt = "boforhold.borPåAdresse.poststed",
                begrunnelse = "for lang verdi",
            ),
            UgyldigSøknadsinnholdInput(
                felt = "forNav.annenGrunn",
                begrunnelse = "for lang verdi",
            ),
        ).tilUgyldigSøknadsinnholdResultat()

        resultat.httpCode shouldBe BadRequest

        val body = deserialize<UgyldigSøknadsinnholdFeilResponse>(resultat.json)
        body shouldBe UgyldigSøknadsinnholdFeilResponse(
            message = "Ugyldig søknadsinnhold",
            code = UGYLDIG_SOKNADSINNHOLD_INPUT_CODE,
            errors = listOf(
                UgyldigSøknadsinnholdValideringsfeilResponse(
                    felt = "formue.eiendomBrukesTil",
                    begrunnelse = "inneholder mistenkelig innhold",
                ),
                UgyldigSøknadsinnholdValideringsfeilResponse(
                    felt = "inntektOgPensjon.andreYtelserINav",
                    begrunnelse = "inneholder kontrolltegn",
                ),
                UgyldigSøknadsinnholdValideringsfeilResponse(
                    felt = "inntektOgPensjon.pensjon.0.ordning",
                    begrunnelse = "mangler ordning",
                ),
                UgyldigSøknadsinnholdValideringsfeilResponse(
                    felt = "formue.kjøretøy.0.kjøretøyDeEier",
                    begrunnelse = "mangler verdi",
                ),
                UgyldigSøknadsinnholdValideringsfeilResponse(
                    felt = "boforhold.borPåAdresse.poststed",
                    begrunnelse = "for lang verdi",
                ),
                UgyldigSøknadsinnholdValideringsfeilResponse(
                    felt = "forNav.annenGrunn",
                    begrunnelse = "for lang verdi",
                ),
            ),
        )
    }

    @Test
    fun `mapper feil fra validator til ugyldig søknadsinnhold resultat`() {
        val base = gyldigUføreSøknad()
        val søknad = base.copy(
            inntektOgPensjon = base.inntektOgPensjon.copy(
                andreYtelserINav = "hei\u0000",
            ),
        )

        val resultat = SøknadsinnholdInputValidator
            .valider(søknad)
            .tilUgyldigSøknadsinnholdResultat()

        resultat.httpCode shouldBe BadRequest

        val body = deserialize<UgyldigSøknadsinnholdFeilResponse>(resultat.json)
        body.errors shouldBe listOf(
            UgyldigSøknadsinnholdValideringsfeilResponse(
                felt = "inntektOgPensjon.andreYtelserINav",
                begrunnelse = "inneholder kontrolltegn",
            ),
        )
    }

    @Test
    fun `alle tekstvalideringer returnerer felt med dot-notasjon`() {
        val ugyldigTekst = "x\u0000"
        val søknad = SøknadsinnholdUføreJson(
            uførevedtak = UførevedtakJson(harUførevedtak = true),
            flyktningsstatus = FlyktningsstatusJson(registrertFlyktning = true),
            personopplysninger = FnrJsonWrapper(fnr = "12345678901"),
            boforhold = BoforholdJson(
                borOgOppholderSegINorge = true,
                delerBoligMedVoksne = false,
                delerBoligMed = null,
                ektefellePartnerSamboer = null,
                innlagtPåInstitusjon = null,
                borPåAdresse = BoforholdJson.AdresseFraSøknad(
                    adresselinje = ugyldigTekst,
                    postnummer = ugyldigTekst,
                    poststed = ugyldigTekst,
                    bruksenhet = ugyldigTekst,
                ),
                ingenAdresseGrunn = null,
            ),
            utenlandsopphold = UtenlandsoppholdJson(),
            oppholdstillatelse = OppholdstillatelseJson(
                erNorskStatsborger = false,
                harOppholdstillatelse = true,
                typeOppholdstillatelse = "midlertidig",
                statsborgerskapAndreLand = true,
                statsborgerskapAndreLandFritekst = ugyldigTekst,
            ),
            inntektOgPensjon = InntektOgPensjonJson(
                andreYtelserINav = ugyldigTekst,
                søktAndreYtelserIkkeBehandletBegrunnelse = ugyldigTekst,
                trygdeytelserIUtlandet = listOf(
                    TrygdeytelserIUtlandetJson(
                        beløp = 1,
                        type = ugyldigTekst,
                        valuta = ugyldigTekst,
                    ),
                ),
                pensjon = listOf(
                    PensjonsOrdningBeløpJson(
                        ordning = ugyldigTekst,
                        beløp = 1.0,
                    ),
                ),
            ),
            formue = FormueJson(
                eierBolig = true,
                boligBrukesTil = ugyldigTekst,
                eiendomBrukesTil = ugyldigTekst,
                kjøretøy = listOf(
                    KjøretøyJson(
                        kjøretøyDeEier = ugyldigTekst,
                        verdiPåKjøretøy = 1,
                    ),
                ),
            ),
            forNav = ForNavJson.Papirsøknad(
                mottaksdatoForSøknad = LocalDate.now(),
                grunnForPapirinnsending = "Annet",
                annenGrunn = ugyldigTekst,
            ),
            ektefelle = EktefelleJson(
                formue = FormueJson(
                    eierBolig = true,
                    boligBrukesTil = ugyldigTekst,
                    eiendomBrukesTil = ugyldigTekst,
                    kjøretøy = listOf(
                        KjøretøyJson(
                            kjøretøyDeEier = ugyldigTekst,
                            verdiPåKjøretøy = 1,
                        ),
                    ),
                ),
                inntektOgPensjon = InntektOgPensjonJson(
                    andreYtelserINav = ugyldigTekst,
                    søktAndreYtelserIkkeBehandletBegrunnelse = ugyldigTekst,
                    trygdeytelserIUtlandet = listOf(
                        TrygdeytelserIUtlandetJson(
                            beløp = 1,
                            type = ugyldigTekst,
                            valuta = ugyldigTekst,
                        ),
                    ),
                    pensjon = listOf(
                        PensjonsOrdningBeløpJson(
                            ordning = ugyldigTekst,
                            beløp = 1.0,
                        ),
                    ),
                ),
            ),
        )

        val resultat = SøknadsinnholdInputValidator
            .valider(søknad)
            .tilUgyldigSøknadsinnholdResultat()

        val body = deserialize<UgyldigSøknadsinnholdFeilResponse>(resultat.json)
        val felt = body.errors.map { it.felt }.toSet()

        felt shouldBe setOf(
            "oppholdstillatelse.statsborgerskapAndreLandFritekst",
            "boforhold.borPåAdresse.adresselinje",
            "boforhold.borPåAdresse.postnummer",
            "boforhold.borPåAdresse.poststed",
            "boforhold.borPåAdresse.bruksenhet",
            "forNav.annenGrunn",
            "formue.boligBrukesTil",
            "formue.eiendomBrukesTil",
            "formue.kjøretøy.0.kjøretøyDeEier",
            "inntektOgPensjon.andreYtelserINav",
            "inntektOgPensjon.søktAndreYtelserIkkeBehandletBegrunnelse",
            "inntektOgPensjon.trygdeytelserIUtlandet.0.type",
            "inntektOgPensjon.trygdeytelserIUtlandet.0.valuta",
            "inntektOgPensjon.pensjon.0.ordning",
            "ektefelle.formue.boligBrukesTil",
            "ektefelle.formue.eiendomBrukesTil",
            "ektefelle.formue.kjøretøy.0.kjøretøyDeEier",
            "ektefelle.inntektOgPensjon.andreYtelserINav",
            "ektefelle.inntektOgPensjon.søktAndreYtelserIkkeBehandletBegrunnelse",
            "ektefelle.inntektOgPensjon.trygdeytelserIUtlandet.0.type",
            "ektefelle.inntektOgPensjon.trygdeytelserIUtlandet.0.valuta",
            "ektefelle.inntektOgPensjon.pensjon.0.ordning",
        )

        body.errors.all { "[" !in it.felt && "]" !in it.felt } shouldBe true
        body.errors.all { it.begrunnelse == "inneholder kontrolltegn" } shouldBe true
    }

    private data class UgyldigSøknadsinnholdFeilResponse(
        val message: String,
        val code: String,
        val errors: List<UgyldigSøknadsinnholdValideringsfeilResponse>,
    )

    private data class UgyldigSøknadsinnholdValideringsfeilResponse(
        val felt: String,
        val begrunnelse: String,
    )

    private fun gyldigUføreSøknad() = SøknadsinnholdUføreJson(
        uførevedtak = UførevedtakJson(harUførevedtak = true),
        flyktningsstatus = FlyktningsstatusJson(registrertFlyktning = true),
        personopplysninger = FnrJsonWrapper(fnr = "12345678901"),
        boforhold = BoforholdJson(
            borOgOppholderSegINorge = true,
            delerBoligMedVoksne = false,
            delerBoligMed = null,
            ektefellePartnerSamboer = null,
            innlagtPåInstitusjon = null,
            borPåAdresse = null,
            ingenAdresseGrunn = OppgittAdresse.IngenAdresse.IngenAdresseGrunn.HAR_IKKE_FAST_BOSTED,
        ),
        utenlandsopphold = UtenlandsoppholdJson(),
        oppholdstillatelse = OppholdstillatelseJson(
            erNorskStatsborger = true,
            harOppholdstillatelse = null,
            typeOppholdstillatelse = null,
            statsborgerskapAndreLand = false,
            statsborgerskapAndreLandFritekst = null,
        ),
        inntektOgPensjon = InntektOgPensjonJson(),
        formue = FormueJson(eierBolig = false),
        forNav = ForNavJson.DigitalSøknad(
            harFullmektigEllerVerge = null,
        ),
        ektefelle = null,
    )
}
