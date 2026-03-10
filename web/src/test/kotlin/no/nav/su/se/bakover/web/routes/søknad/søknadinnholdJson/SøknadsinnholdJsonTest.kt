package no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson

import arrow.core.Either
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.OppgittAdresse
import org.junit.jupiter.api.Test

internal class SøknadsinnholdJsonTest {

    @Test
    fun `mapper ugyldig fnr til left`() {
        val søknad = gyldigAlderssøknad().copy(
            personopplysninger = FnrJsonWrapper("123"),
        )

        val feil = søknad.toSøknadsinnholdAlder().leftOrFail()

        feil shouldBe KunneIkkeLageSøknadinnhold.UgyldigSøknadsinnholdInputWeb(
            underliggendeFeil = listOf(
                UgyldigSøknadsinnholdInputFraJson(
                    felt = "personopplysninger.fnr",
                    begrunnelse = "ugyldig fødselsnummer",
                ),
            ),
        )
    }

    @Test
    fun `mapper ugyldig datoformat til left`() {
        val søknad = gyldigAlderssøknad().copy(
            utenlandsopphold = UtenlandsoppholdJson(
                registrertePerioder = listOf(
                    UtenlandsoppholdPeriodeJson(
                        utreisedato = "2026-13-01",
                        innreisedato = "2026-12-01",
                    ),
                ),
            ),
        )

        val feil = søknad.toSøknadsinnholdAlder().leftOrFail()

        feil shouldBe KunneIkkeLageSøknadinnhold.UgyldigSøknadsinnholdInputWeb(
            underliggendeFeil = listOf(
                UgyldigSøknadsinnholdInputFraJson(
                    felt = "utenlandsopphold.registrertePerioder.0.utreisedato",
                    begrunnelse = "ugyldig datoformat",
                ),
            ),
        )
    }

    @Test
    fun `mapper ugyldig enumverdi til left`() {
        val søknad = gyldigAlderssøknad().copy(
            boforhold = standardBoforhold().copy(
                delerBoligMedVoksne = true,
                delerBoligMed = "UKJENT",
            ),
        )

        val feil = søknad.toSøknadsinnholdAlder().leftOrFail()

        feil shouldBe KunneIkkeLageSøknadinnhold.UgyldigSøknadsinnholdInputWeb(
            underliggendeFeil = listOf(
                UgyldigSøknadsinnholdInputFraJson(
                    felt = "boforhold.delerBoligMed",
                    begrunnelse = "Ukjent verdi: UKJENT",
                ),
            ),
        )
    }

    private fun <T> Either<KunneIkkeLageSøknadinnhold, T>.leftOrFail(): KunneIkkeLageSøknadinnhold =
        when (this) {
            is Either.Left -> value
            is Either.Right -> error("Forventet Left, fikk Right")
        }

    private fun gyldigAlderssøknad() = SøknadsinnholdAlderJson(
        harSøktAlderspensjon = HarSøktAlderspensjonJson(harSøktAlderspensjon = false),
        oppholdstillatelseAlder = OppholdstillatelseAlderJson(eøsborger = null, familieforening = null),
        personopplysninger = FnrJsonWrapper(fnr = "12345678910"),
        boforhold = standardBoforhold(),
        utenlandsopphold = UtenlandsoppholdJson(),
        oppholdstillatelse = standardOppholdstillatelse(),
        inntektOgPensjon = InntektOgPensjonJson(),
        formue = FormueJson(eierBolig = false),
        forNav = ForNavJson.DigitalSøknad(),
        ektefelle = null,
    )

    private fun standardBoforhold() = BoforholdJson(
        borOgOppholderSegINorge = true,
        delerBoligMedVoksne = false,
        delerBoligMed = null,
        ektefellePartnerSamboer = null,
        innlagtPåInstitusjon = null,
        borPåAdresse = null,
        ingenAdresseGrunn = OppgittAdresse.IngenAdresse.IngenAdresseGrunn.HAR_IKKE_FAST_BOSTED,
    )

    private fun standardOppholdstillatelse() = OppholdstillatelseJson(
        erNorskStatsborger = true,
        harOppholdstillatelse = null,
        typeOppholdstillatelse = null,
        statsborgerskapAndreLand = false,
        statsborgerskapAndreLandFritekst = null,
    )
}
