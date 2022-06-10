package no.nav.su.se.bakover.web.routes.søknad

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.søknadinnhold.EktefellePartnerSamboer
import no.nav.su.se.bakover.domain.søknadinnhold.OppgittAdresse
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.BoforholdJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.EktefelleJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.FeilVedOpprettelseAvSøknadinnhold
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.FeilVedValideringAvBoforholdOgEktefelle
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.ForNavJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.FormueJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.HarSøktAlderspensjonJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.InntektOgPensjonJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.OppholdstillatelseAlderJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.OppholdstillatelseJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.PersonopplysningerJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdAlderJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.UtenlandsoppholdJson
import org.junit.jupiter.api.Test

internal class SøknadinnholdTest {

    private val formueJson = FormueJson(
        eierBolig = false,
        borIBolig = null,
        verdiPåBolig = null,
        boligBrukesTil = null,
        depositumsBeløp = 1,
        verdiPåEiendom = null,
        eiendomBrukesTil = null,
        kjøretøy = listOf(),
        innskuddsBeløp = null,
        verdipapirBeløp = null,
        skylderNoenMegPengerBeløp = null,
        kontanterBeløp = null,
    )
    private val inntektOgPensjonJson = InntektOgPensjonJson(
        forventetInntekt = null,
        andreYtelserINav = null,
        andreYtelserINavBeløp = null,
        søktAndreYtelserIkkeBehandletBegrunnelse = null,
        trygdeytelserIUtlandet = listOf(),
        pensjon = listOf(),
    )

    private val alderJson = SøknadsinnholdAlderJson(
        harSøktAlderspensjon = HarSøktAlderspensjonJson(
            harSøktAlderspensjon = true,
        ),
        oppholdstillatelseAlder = OppholdstillatelseAlderJson(
            eøsborger = true,
            familieforening = true,
        ),
        personopplysninger = PersonopplysningerJson(fnr = "12345678901"),
        boforhold = BoforholdJson(
            borOgOppholderSegINorge = true,
            delerBoligMedVoksne = true,
            delerBoligMed = "VOKSNE_BARN",
            ektefellePartnerSamboer = null,
            innlagtPåInstitusjon = null,
            borPåAdresse = null,
            ingenAdresseGrunn = OppgittAdresse.IngenAdresse.IngenAdresseGrunn.BOR_PÅ_ANNEN_ADRESSE,
        ),
        utenlandsopphold = UtenlandsoppholdJson(
            registrertePerioder = emptyList(),
            planlagtePerioder = emptyList(),
        ),
        oppholdstillatelse = OppholdstillatelseJson(
            erNorskStatsborger = false,
            harOppholdstillatelse = true,
            typeOppholdstillatelse = "permanent",
            statsborgerskapAndreLand = false,
            statsborgerskapAndreLandFritekst = null,
        ),
        inntektOgPensjon = inntektOgPensjonJson,
        formue = formueJson,
        forNav = ForNavJson.DigitalSøknad(harFullmektigEllerVerge = null),
        ektefelle = null,
    )

    @Test
    fun `validerer oppholdstillatelse og oppholdstillatelseAlder`() {
        alderJson.toSøknadsinnholdAlder().shouldBeRight()

        alderJson.copy(
            oppholdstillatelse = OppholdstillatelseJson(
                erNorskStatsborger = false,
                harOppholdstillatelse = false,
                typeOppholdstillatelse = null,
                statsborgerskapAndreLand = false,
                statsborgerskapAndreLandFritekst = null,
            ),
            oppholdstillatelseAlder = OppholdstillatelseAlderJson(
                eøsborger = null,
                familieforening = null,
            ),
        ).toSøknadsinnholdAlder() shouldBe FeilVedOpprettelseAvSøknadinnhold.DataVedOpphodlstillatelseErInkonsekvent(
            FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder.EøsBorgerErIkkeutfylt,
        ).left()
    }

    @Test
    fun `validerer boforhold og ektefelle`() {
        alderJson.copy(
            boforhold = BoforholdJson(
                borOgOppholderSegINorge = true,
                delerBoligMedVoksne = true,
                delerBoligMed = "EKTEMAKE_SAMBOER",
                ektefellePartnerSamboer = EktefellePartnerSamboer(false, Fnr("12345678901")),
                innlagtPåInstitusjon = null,
                borPåAdresse = null,
                ingenAdresseGrunn = OppgittAdresse.IngenAdresse.IngenAdresseGrunn.BOR_PÅ_ANNEN_ADRESSE,
            ),
            ektefelle = EktefelleJson(
                formue = formueJson, inntektOgPensjon = inntektOgPensjonJson,
            ),
        ).toSøknadsinnholdAlder().shouldBeRight()

        alderJson.copy(
            boforhold = BoforholdJson(
                borOgOppholderSegINorge = true,
                delerBoligMedVoksne = true,
                delerBoligMed = "EKTEMAKE_SAMBOER",
                ektefellePartnerSamboer = EktefellePartnerSamboer(false, Fnr("12345678901")),
                innlagtPåInstitusjon = null,
                borPåAdresse = null,
                ingenAdresseGrunn = OppgittAdresse.IngenAdresse.IngenAdresseGrunn.BOR_PÅ_ANNEN_ADRESSE,
            ),
            ektefelle = null,
        )
            .toSøknadsinnholdAlder() shouldBe FeilVedOpprettelseAvSøknadinnhold.DataVedBoforholdOgEktefelleErInkonsekvent(
            FeilVedValideringAvBoforholdOgEktefelle.EktefelleErIkkeutfylt,
        ).left()
    }
}
