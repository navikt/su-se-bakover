package no.nav.su.se.bakover.domain.søknadinnhold

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.januar
import org.junit.jupiter.api.Test

internal class BoforholdTest() {
    private val oppgittAdresse = OppgittAdresse.BorPåAdresse(
        adresselinje = "Brugata 55",
        postnummer = "0183",
        poststed = null,
        bruksenhet = null,
    )

    @Test
    fun `hvem søker deler bolig med må være utfylt dersom delerBolig er true`() {
        Boforhold.tryCreate(
            borOgOppholderSegINorge = false,
            delerBolig = true,
            delerBoligMed = null,
            ektefellePartnerSamboer = null,
            innlagtPåInstitusjon = null,
            oppgittAdresse = oppgittAdresse,
        ) shouldBe FeilVedOpprettelseAvBoforhold.DelerBoligMedErIkkeUtfylt.left()

        Boforhold.tryCreate(
            borOgOppholderSegINorge = false,
            delerBolig = true,
            delerBoligMed = Boforhold.DelerBoligMed.VOKSNE_BARN,
            ektefellePartnerSamboer = null,
            innlagtPåInstitusjon = null,
            oppgittAdresse = oppgittAdresse,
        ).shouldBeRight()
    }

    @Test
    fun `ektefellePartnerSamboer må være fyllt ut dersom søker delerBoligMed EPS`() {
        Boforhold.tryCreate(
            borOgOppholderSegINorge = false,
            delerBolig = true,
            delerBoligMed = Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER,
            ektefellePartnerSamboer = null,
            innlagtPåInstitusjon = null,
            oppgittAdresse = oppgittAdresse,
        ) shouldBe FeilVedOpprettelseAvBoforhold.EktefellePartnerSamboerMåVæreUtfylt.left()

        Boforhold.tryCreate(
            borOgOppholderSegINorge = false,
            delerBolig = true,
            delerBoligMed = Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER,
            ektefellePartnerSamboer = EktefellePartnerSamboer(erUførFlyktning = null, fnr = Fnr("12345678901")),
            innlagtPåInstitusjon = null,
            oppgittAdresse = oppgittAdresse,
        ).shouldBeRight()
    }

    @Test
    fun `får feil dersom datoForUtskrivelse er satt og fortsattInnlagt er true`() {
        Boforhold.tryCreate(
            borOgOppholderSegINorge = false,
            delerBolig = true,
            delerBoligMed = Boforhold.DelerBoligMed.EKTEMAKE_SAMBOER,
            ektefellePartnerSamboer = EktefellePartnerSamboer(erUførFlyktning = null, fnr = Fnr("12345678901")),
            innlagtPåInstitusjon = InnlagtPåInstitusjon(1.januar(2021), 31.januar(2021), true),
            oppgittAdresse = oppgittAdresse,
        ) shouldBe FeilVedOpprettelseAvBoforhold.InkonsekventInnleggelse.left()
    }

    @Test
    fun `viser gateadresse og postadresse`() {
        oppgittAdresse.toString() shouldBe "Brugata 55, 0183"
    }

    @Test
    fun `bruksenhetsnummer vises i gateadressen`() {
        val oppgittAdresse = OppgittAdresse.BorPåAdresse(
            adresselinje = "Brugata 55",
            postnummer = "0183",
            poststed = null,
            bruksenhet = "H0401",
        )
        oppgittAdresse.toString() shouldBe "Brugata 55 H0401, 0183"
    }

    @Test
    fun `poststed vises i postadressen`() {
        val oppgittAdresse = OppgittAdresse.BorPåAdresse(
            adresselinje = "Brugata 55",
            postnummer = "0183",
            poststed = "Oslo",
            bruksenhet = null,
        )
        oppgittAdresse.toString() shouldBe "Brugata 55, 0183 Oslo"
    }

    @Test
    fun `viser all adresseinformasjon`() {
        val oppgittAdresse = OppgittAdresse.BorPåAdresse(
            adresselinje = "Brugata 55",
            postnummer = "0183",
            poststed = "Oslo",
            bruksenhet = "H0401",
        )
        oppgittAdresse.toString() shouldBe "Brugata 55 H0401, 0183 Oslo"
    }
}
