package no.nav.su.se.bakover.domain.brev.søknad.lukk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.ddMMyyyy
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Person.Navn
import no.nav.su.se.bakover.domain.brev.BrevConfig
import no.nav.su.se.bakover.domain.brev.BrevInnhold.Personalia
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class AvvistSøknadBrevRequestTest {

    private val expectedPersonalia = Personalia(
        dato = LocalDate.now().ddMMyyyy(),
        fødselsnummer = Fnr(fnr = "12345678901"),
        fornavn = "Tore",
        etternavn = "Strømøy",
    )

    private val person = Person(
        ident = Ident(
            fnr = Fnr(fnr = "12345678901"),
            aktørId = AktørId(aktørId = "123"),
        ),
        navn = Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
    )

    @Test
    fun `lager vedtaks-brevdata`() {
        AvvistSøknadBrevRequest(
            person,
            BrevConfig.Vedtak(null),
            "saksbehandler",
        ).brevInnhold shouldBe AvvistSøknadVedtakBrevInnhold(
            expectedPersonalia,
            "saksbehandler",
            null,
        )
    }

    @Test
    fun `lager vedtaks-brevdata med fritekst`() {
        AvvistSøknadBrevRequest(
            person,
            BrevConfig.Vedtak("jeg er fritekst"),
            "saksbehandler",
        ).brevInnhold shouldBe AvvistSøknadVedtakBrevInnhold(
            expectedPersonalia,
            "saksbehandler",
            "jeg er fritekst",
        )
    }

    @Test
    fun `lager fritekst-brevdata`() {
        AvvistSøknadBrevRequest(
            person,
            BrevConfig.Fritekst(
                "jeg er fritekst",
            ),
            "saksbehandler",
        ).brevInnhold shouldBe AvvistSøknadFritekstBrevInnhold(
            personalia = expectedPersonalia,
            saksbehandlerNavn = "saksbehandler",
            fritekst = "jeg er fritekst",
        )
    }
}
