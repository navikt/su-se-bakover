package no.nav.su.se.bakover.domain.brev.søknad.lukk

import arrow.core.right
import dokument.domain.brev.Brevvalg
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.extensions.ddMMyyyy
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.domain.brev.command.AvvistSøknadDokumentCommand
import no.nav.su.se.bakover.domain.brev.jsonRequest.PersonaliaPdfInnhold
import no.nav.su.se.bakover.domain.brev.jsonRequest.tilPdfInnhold
import no.nav.su.se.bakover.domain.brev.søknad.lukk.avvist.AvvistSøknadFritekstPdfInnhold
import no.nav.su.se.bakover.domain.brev.søknad.lukk.avvist.AvvistSøknadVedtakPdfInnhold
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedLocalDate
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import person.domain.Person
import person.domain.Person.Navn
import java.time.Year

internal class AvvistSøknadDokumentCommandTest {

    private val expectedPersonalia = PersonaliaPdfInnhold(
        dato = fixedLocalDate.ddMMyyyy(),
        fødselsnummer = "12345678901",
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
        AvvistSøknadDokumentCommand(
            brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.Vedtaksbrev.MedFritekst(null, "fritekst"),
            saksbehandler = saksbehandler,
            fødselsnummer = person.ident.fnr,
            saksnummer = Saksnummer(2021),
        ).tilPdfInnhold(
            clock = fixedClock,
            hentPerson = { person.right() },
            hentNavnForIdent = { "saksbehandler".right() },
        ).getOrFail() shouldBe AvvistSøknadVedtakPdfInnhold(
            expectedPersonalia,
            "saksbehandler",
            "fritekst",
        )
    }

    @Test
    fun `lager fritekst-brevdata`() {
        AvvistSøknadDokumentCommand(
            brevvalg = Brevvalg.SaksbehandlersValg.SkalSendeBrev.InformasjonsbrevMedFritekst("jeg er fritekst"),
            saksbehandler = saksbehandler,
            fødselsnummer = person.ident.fnr,
            saksnummer = Saksnummer(2021),
        ).tilPdfInnhold(
            clock = fixedClock,
            hentPerson = { person.right() },
            hentNavnForIdent = { "saksbehandler".right() },
        ).getOrFail() shouldBe AvvistSøknadFritekstPdfInnhold(
            personalia = expectedPersonalia,
            saksbehandlerNavn = "saksbehandler",
            fritekst = "jeg er fritekst",
        )
    }
}
