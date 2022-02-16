package no.nav.su.se.bakover.domain

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.søknad.Boforhold
import org.junit.jupiter.api.Test

internal class BoforholdTest() {
    @Test
    fun `viser gateadresse og postadresse`() {
        val oppgittAdresse = Boforhold.OppgittAdresse.BorPåAdresse(
            adresselinje = "Brugata 55",
            postnummer = "0183",
            poststed = null,
            bruksenhet = null,
        )
        oppgittAdresse.toString() shouldBe "Brugata 55, 0183"
    }

    @Test
    fun `bruksenhetsnummer vises i gateadressen`() {
        val oppgittAdresse = Boforhold.OppgittAdresse.BorPåAdresse(
            adresselinje = "Brugata 55",
            postnummer = "0183",
            poststed = null,
            bruksenhet = "H0401"
        )
        oppgittAdresse.toString() shouldBe "Brugata 55 H0401, 0183"
    }

    @Test
    fun `poststed vises i postadressen`() {
        val oppgittAdresse = Boforhold.OppgittAdresse.BorPåAdresse(
            adresselinje = "Brugata 55",
            postnummer = "0183",
            poststed = "Oslo",
            bruksenhet = null
        )
        oppgittAdresse.toString() shouldBe "Brugata 55, 0183 Oslo"
    }

    @Test
    fun `viser all adresseinformasjon`() {
        val oppgittAdresse = Boforhold.OppgittAdresse.BorPåAdresse(
            adresselinje = "Brugata 55",
            postnummer = "0183",
            poststed = "Oslo",
            bruksenhet = "H0401"
        )
        oppgittAdresse.toString() shouldBe "Brugata 55 H0401, 0183 Oslo"
    }
}
