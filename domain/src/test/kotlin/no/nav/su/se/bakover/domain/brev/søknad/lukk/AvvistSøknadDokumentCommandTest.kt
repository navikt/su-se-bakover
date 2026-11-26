package no.nav.su.se.bakover.domain.brev.søknad.lukk

import arrow.core.right
import dokument.domain.generer.BrevmalCommand
import dokument.domain.generer.PersonaliaPdfInnhold
import dokument.domain.generer.bygger.maler.AvvistSøknadVedtakPdfInnhold
import dokument.domain.generer.bygger.maler.avvistSøknadVedtakBrev
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.domain.tid.ddMMyyyy
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.domain.brev.jsonRequest.tilPdfInnhold
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
        BrevmalCommand(
            mal = avvistSøknadVedtakBrev,
            fakta = "fritekst",
            fødselsnummer = person.ident.fnr,
            saksnummer = Saksnummer(2021),
            sakstype = Sakstype.UFØRE,
            saksbehandler = saksbehandler,
            attestant = null,
        ).tilPdfInnhold(
            clock = fixedClock,
            hentPerson = { person.right() },
            hentNavnForIdent = { "saksbehandler".right() },
        ).getOrFail() shouldBe AvvistSøknadVedtakPdfInnhold(
            Sakstype.UFØRE,
            expectedPersonalia,
            "saksbehandler",
            "fritekst",
        )
    }
}
