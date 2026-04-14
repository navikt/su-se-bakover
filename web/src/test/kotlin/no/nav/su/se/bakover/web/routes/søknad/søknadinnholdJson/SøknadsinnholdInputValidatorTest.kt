package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.OppgittAdresse
import org.junit.jupiter.api.Test

internal class SøknadsinnholdInputValidatorTest {

    @Test
    fun `godtar vanlig tekst`() {
        val søknad = gyldigUføreSøknad()

        SøknadsinnholdInputValidator.valider(søknad).isEmpty() shouldBe true
    }

    @Test
    fun `skal tillate paragraftegn`() {
        val søknad = gyldigUføreSøknad().copy(
            inntektOgPensjon = InntektOgPensjonJson(
                andreYtelserINav = "sosialstønad § 3-2",
            ),
        )

        SøknadsinnholdInputValidator.valider(søknad).isEmpty() shouldBe true
    }

    @Test
    fun `avviser mistenkelig tekst`() {
        val søknad = gyldigUføreSøknad().copy(
            formue = gyldigUføreSøknad().formue.copy(
                eiendomBrukesTil = "<script>alert(1)</script>",
            ),
        )

        val feil = SøknadsinnholdInputValidator.valider(søknad)

        feil.any { it.felt == "formue.eiendomBrukesTil" } shouldBe true
    }

    @Test
    fun `avviser forbudte kontrolltegn`() {
        val søknad = gyldigUføreSøknad().copy(
            inntektOgPensjon = InntektOgPensjonJson(
                andreYtelserINav = "hei\u0000",
            ),
        )

        val feil = SøknadsinnholdInputValidator.valider(søknad)

        feil.any { it.felt == "inntektOgPensjon.andreYtelserINav" } shouldBe true
    }

    @Test
    fun `returnerer kun første feil per felt`() {
        val søknad = gyldigUføreSøknad().copy(
            inntektOgPensjon = InntektOgPensjonJson(
                andreYtelserINav = "hei\u0000<script>",
            ),
        )

        val feilForFelt = SøknadsinnholdInputValidator
            .valider(søknad)
            .filter { it.felt == "inntektOgPensjon.andreYtelserINav" }

        feilForFelt.size shouldBe 1
        feilForFelt.first().begrunnelse shouldBe "inneholder kontrolltegn"
    }

    @Test
    fun `godtar norske bokstaver og tillatte skilletegn`() {
        val søknad = gyldigUføreSøknad().copy(
            inntektOgPensjon = InntektOgPensjonJson(
                andreYtelserINav = "ÆØÅ æøå. Hei, går det bra? (ja) 50% *",
            ),
        )

        SøknadsinnholdInputValidator.valider(søknad).isEmpty() shouldBe true
    }

    @Test
    fun `avviser tegn utenfor ascii og norske bokstaver`() {
        val søknad = gyldigUføreSøknad().copy(
            inntektOgPensjon = InntektOgPensjonJson(
                andreYtelserINav = "hei €",
            ),
        )

        val feil = SøknadsinnholdInputValidator.valider(søknad)

        feil.any {
            it.felt == "inntektOgPensjon.andreYtelserINav" && it.begrunnelse.contains("tillatt tegnsett")
        } shouldBe true
    }

    @Test
    fun `validerer ikke beløpsfelter med tekstregler`() {
        val base = gyldigUføreSøknad()
        val søknad = base.copy(
            inntektOgPensjon = base.inntektOgPensjon.copy(
                forventetInntekt = -1000,
                andreYtelserINavBeløp = -50,
                trygdeytelserIUtlandet = listOf(
                    TrygdeytelserIUtlandetJson(
                        beløp = -1,
                        type = "trygd",
                        valuta = "NOK",
                    ),
                ),
                pensjon = listOf(
                    PensjonsOrdningBeløpJson(
                        ordning = "KLP",
                        beløp = -1.0,
                    ),
                ),
            ),
            formue = base.formue.copy(
                depositumsBeløp = -1,
                verdiPåEiendom = -1,
                kjøretøy = listOf(
                    KjøretøyJson(
                        verdiPåKjøretøy = -1,
                        kjøretøyDeEier = "bil",
                    ),
                ),
                innskuddsBeløp = -1,
                verdipapirBeløp = -1,
                skylderNoenMegPengerBeløp = -1,
                kontanterBeløp = -1,
            ),
        )

        SøknadsinnholdInputValidator.valider(søknad).isEmpty() shouldBe true
    }

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
        inntektOgPensjon = InntektOgPensjonJson(
            andreYtelserINav = "sosialstønad",
            søktAndreYtelserIkkeBehandletBegrunnelse = "mangler avklaring",
            trygdeytelserIUtlandet = listOf(
                TrygdeytelserIUtlandetJson(
                    beløp = 1,
                    type = "trygd",
                    valuta = "NOK",
                ),
            ),
            pensjon = listOf(
                PensjonsOrdningBeløpJson(
                    ordning = "KLP",
                    beløp = 1.0,
                ),
            ),
        ),
        formue = FormueJson(
            eierBolig = true,
            borIBolig = false,
            verdiPåBolig = 1000,
            boligBrukesTil = "utleie",
            depositumsBeløp = 1000,
            verdiPåEiendom = 1000,
            eiendomBrukesTil = "fritidsbolig",
            kjøretøy = listOf(KjøretøyJson(verdiPåKjøretøy = 1000, kjøretøyDeEier = "bil")),
            innskuddsBeløp = 1000,
            verdipapirBeløp = 1000,
            skylderNoenMegPengerBeløp = 1000,
            kontanterBeløp = 1000,
        ),
        forNav = ForNavJson.DigitalSøknad(
            harFullmektigEllerVerge = null,
        ),
        ektefelle = null,
    )
}
