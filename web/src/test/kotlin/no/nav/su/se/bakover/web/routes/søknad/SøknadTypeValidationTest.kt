package no.nav.su.se.bakover.web.routes.søknad

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import no.nav.su.se.bakover.domain.søknad.søknadinnhold.OppgittAdresse
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.BoforholdJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.FlyktningsstatusJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.FnrJsonWrapper
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.ForNavJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.FormueJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.HarSøktAlderspensjonJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.InntektOgPensjonJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.OppholdstillatelseAlderJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.OppholdstillatelseJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdAlderJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.SøknadsinnholdUføreJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.UførevedtakJson
import no.nav.su.se.bakover.web.routes.søknad.søknadinnholdJson.UtenlandsoppholdJson
import org.junit.jupiter.api.Test

internal class SøknadTypeValidationTest {

    @Test
    fun `godtar samsvar mellom path og body for uføre`() {
        validerSøknadstypePathMotBody("ufore", gyldigUføreSøknad()) shouldBe null
        validerSøknadstypePathMotBody("uføre", gyldigUføreSøknad()) shouldBe null
    }

    @Test
    fun `avviser mismatch mellom path og body`() {
        val resultat = validerSøknadstypePathMotBody("alder", gyldigUføreSøknad())

        resultat?.httpCode shouldBe BadRequest
    }

    @Test
    fun `avviser ukjent type i path`() {
        val resultat = validerSøknadstypePathMotBody("hva_som_helst", gyldigAlderssøknad())

        resultat?.httpCode shouldBe BadRequest
    }

    private fun gyldigUføreSøknad() = SøknadsinnholdUføreJson(
        uførevedtak = UførevedtakJson(harUførevedtak = true),
        flyktningsstatus = FlyktningsstatusJson(registrertFlyktning = true),
        personopplysninger = FnrJsonWrapper(fnr = "12345678901"),
        boforhold = standardBoforhold(),
        utenlandsopphold = UtenlandsoppholdJson(),
        oppholdstillatelse = standardOppholdstillatelse(),
        inntektOgPensjon = InntektOgPensjonJson(),
        formue = FormueJson(eierBolig = false),
        forNav = ForNavJson.DigitalSøknad(),
        ektefelle = null,
    )

    private fun gyldigAlderssøknad() = SøknadsinnholdAlderJson(
        harSøktAlderspensjon = HarSøktAlderspensjonJson(harSøktAlderspensjon = false),
        oppholdstillatelseAlder = OppholdstillatelseAlderJson(eøsborger = null, familieforening = null),
        personopplysninger = FnrJsonWrapper(fnr = "12345678901"),
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
