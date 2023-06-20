package no.nav.su.se.bakover.domain.brev.søknad.lukk

import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.extensions.ddMMyyyy
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.domain.brev.Brevvalg
import no.nav.su.se.bakover.domain.brev.PdfInnhold.Personalia
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.person.Person.Navn
import no.nav.su.se.bakover.domain.sak.Saksnummer
import no.nav.su.se.bakover.test.fixedLocalDate
import org.junit.jupiter.api.Test
import java.time.Year

internal class AvvistSøknadBrevRequestTest {

    private val expectedPersonalia = Personalia(
        dato = fixedLocalDate.ddMMyyyy(),
        fødselsnummer = Fnr(fnr = "12345678901"),
        fornavn = "Tore",
        etternavn = "Strømøy",
        saksnummer = 2021,
    )

    private val person = Person(
        ident = Ident(
            fnr = Fnr(fnr = "12345678901"),
            aktørId = AktørId(aktørId = "123"),
        ),
        navn = Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
        fødsel = Person.Fødsel.MedFødselsår(
            år = Year.of(1956),
        ),
    )

    @Test
    fun `lager vedtaks-brevdata`() {
        AvvistSøknadBrevRequest(
            person = person,
            brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst(null, "fritekst"),
            saksbehandlerNavn = "saksbehandler",
            dagensDato = fixedLocalDate,
            saksnummer = Saksnummer(2021),
        ).pdfInnhold shouldBe AvvistSøknadVedtakPdfInnhold(
            expectedPersonalia,
            "saksbehandler",
            "fritekst",
        )
    }

    @Test
    fun `lager fritekst-brevdata`() {
        AvvistSøknadBrevRequest(
            person = person,
            brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst("jeg er fritekst"),
            saksbehandlerNavn = "saksbehandler",
            dagensDato = fixedLocalDate,
            saksnummer = Saksnummer(2021),
        ).pdfInnhold shouldBe AvvistSøknadFritekstPdfInnhold(
            personalia = expectedPersonalia,
            saksbehandlerNavn = "saksbehandler",
            fritekst = "jeg er fritekst",
        )
    }
}
