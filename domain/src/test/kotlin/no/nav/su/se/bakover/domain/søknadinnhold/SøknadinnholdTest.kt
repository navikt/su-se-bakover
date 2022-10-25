package no.nav.su.se.bakover.domain.søknadinnhold

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.søknad.boforhold
import no.nav.su.se.bakover.test.søknad.ektefelle
import no.nav.su.se.bakover.test.søknad.forNavDigitalSøknad
import no.nav.su.se.bakover.test.søknad.formue
import no.nav.su.se.bakover.test.søknad.inntektOgPensjon
import no.nav.su.se.bakover.test.søknad.oppholdstillatelse
import no.nav.su.se.bakover.test.søknad.personopplysninger
import no.nav.su.se.bakover.test.søknad.utenlandsopphold
import org.junit.jupiter.api.Test

internal class SøknadinnholdTest {

    @Test
    fun `forventer at eøsborger er utfylt dersom søker ikke er norsk statsborger`() {
        SøknadsinnholdAlder.tryCreate(
            harSøktAlderspensjon = HarSøktAlderspensjon(harSøktAlderspensjon = false),
            oppholdstillatelseAlder = OppholdstillatelseAlder(
                eøsborger = null,
                familiegjenforening = null,
            ),
            personopplysninger = personopplysninger(),
            boforhold = boforhold(),
            utenlandsopphold = utenlandsopphold(),
            oppholdstillatelse = Oppholdstillatelse.tryCreate(
                erNorskStatsborger = false,
                harOppholdstillatelse = false,
                oppholdstillatelseType = null,
                statsborgerskapAndreLand = false,
                statsborgerskapAndreLandFritekst = null,
            ).getOrFail(),
            inntektOgPensjon = inntektOgPensjon(),
            formue = formue(),
            forNav = forNavDigitalSøknad(),
            ektefelle = ektefelle(),
        ) shouldBe FeilVedOpprettelseAvSøknadinnhold.DataVedOpphodlstillatelseErInkonsekvent(
            FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder.EøsBorgerErIkkeutfylt,
        ).left()
    }

    @Test
    fun `forventer at familiegjenforening er utfylt dersom søker har oppholdstillatelse`() {
        SøknadsinnholdAlder.tryCreate(
            harSøktAlderspensjon = HarSøktAlderspensjon(harSøktAlderspensjon = false),
            oppholdstillatelseAlder = OppholdstillatelseAlder(
                eøsborger = false,
                familiegjenforening = null,
            ),
            personopplysninger = personopplysninger(),
            boforhold = boforhold(),
            utenlandsopphold = utenlandsopphold(),
            oppholdstillatelse = Oppholdstillatelse.tryCreate(
                erNorskStatsborger = false,
                harOppholdstillatelse = true,
                oppholdstillatelseType = Oppholdstillatelse.OppholdstillatelseType.PERMANENT,
                statsborgerskapAndreLand = false,
                statsborgerskapAndreLandFritekst = null,
            ).getOrFail(),
            inntektOgPensjon = inntektOgPensjon(),
            formue = formue(),
            forNav = forNavDigitalSøknad(),
            ektefelle = ektefelle(),
        ) shouldBe FeilVedOpprettelseAvSøknadinnhold.DataVedOpphodlstillatelseErInkonsekvent(
            FeilVedValideringAvOppholdstillatelseOgOppholdstillatelseAlder.FamiliegjenforeningErIkkeutfylt,
        ).left()
    }

    @Test
    fun `forventer at formue og inntekt for ektefelle er utfylt dersom søker har EPS ved boforhold`() {
        SøknadsinnholdAlder.tryCreate(
            harSøktAlderspensjon = HarSøktAlderspensjon(harSøktAlderspensjon = false),
            oppholdstillatelseAlder = OppholdstillatelseAlder(
                eøsborger = false,
                familiegjenforening = false,
            ),
            personopplysninger = personopplysninger(),
            boforhold = Boforhold.tryCreate(
                borOgOppholderSegINorge = true,
                delerBolig = true,
                delerBoligMed = Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER,
                ektefellePartnerSamboer = EktefellePartnerSamboer(true, Fnr("12345678901")),
                innlagtPåInstitusjon = null,
                oppgittAdresse = OppgittAdresse.IngenAdresse(OppgittAdresse.IngenAdresse.IngenAdresseGrunn.HAR_IKKE_FAST_BOSTED),
            ).getOrFail(),
            utenlandsopphold = utenlandsopphold(),
            oppholdstillatelse = oppholdstillatelse(),
            inntektOgPensjon = inntektOgPensjon(),
            formue = formue(),
            forNav = forNavDigitalSøknad(),
            ektefelle = null,
        ) shouldBe FeilVedOpprettelseAvSøknadinnhold.DataVedBoforholdOgEktefelleErInkonsekvent(
            FeilVedValideringAvBoforholdOgEktefelle.EktefelleErIkkeutfylt,
        ).left()
    }
}
