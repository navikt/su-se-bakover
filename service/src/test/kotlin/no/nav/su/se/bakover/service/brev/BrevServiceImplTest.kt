package no.nav.su.se.bakover.service.brev

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokarkiv.Journalpost
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.client.pdf.KunneIkkeGenererePdf
import no.nav.su.se.bakover.client.pdf.PdfGenerator
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.BrevTemplate
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.person.PersonOppslag
import no.nav.su.se.bakover.domain.person.PersonOppslag.KunneIkkeHentePerson
import no.nav.su.se.bakover.service.argThat
import org.junit.jupiter.api.Test
import java.util.UUID

internal class BrevServiceImplTest {

    private val person = Person(
        ident = Ident(
            fnr = Fnr(fnr = "12345678901"),
            aktørId = AktørId(aktørId = "123")
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = null, etternavn = "Strømøy"),
        telefonnummer = null,
        adresse = null,
        statsborgerskap = null,
        kjønn = null,
        adressebeskyttelse = null,
        skjermet = null,
        kontaktinfo = null,
        vergemaalEllerFremtidsfullmakt = null
    )

    @Test
    fun `lager brev`() {
        val pdf = "".toByteArray()
        val personMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }

        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<BrevInnhold>()) } doReturn pdf.right()
        }

        BrevServiceImpl(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personMock,
            dokArkiv = mock(),
            dokDistFordeling = mock()
        ).lagBrev(DummyRequest) shouldBe pdf.right()

        verify(personMock).person(DummyRequest.getFnr())
        verify(pdfGeneratorMock).genererPdf(DummyBrevInnhold)
    }

    @Test
    fun `lager ikke brev når person kall failer`() {
        val personMock = mock<PersonOppslag> {
            on { person(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        BrevServiceImpl(
            pdfGenerator = mock(),
            personOppslag = personMock,
            dokArkiv = mock(),
            dokDistFordeling = mock()
        ).lagBrev(DummyRequest) shouldBe KunneIkkeLageBrev.FantIkkePerson.left()
        verify(personMock).person(DummyRequest.getFnr())
    }

    @Test
    fun `lager ikke brev når pdf-generator kall failer`() {
        val personMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }
        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(DummyBrevInnhold) } doReturn KunneIkkeGenererePdf.left()
        }

        BrevServiceImpl(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personMock,
            dokArkiv = mock(),
            dokDistFordeling = mock()
        ).lagBrev(DummyRequest) shouldBe KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        verify(personMock).person(DummyRequest.getFnr())
        verify(pdfGeneratorMock).genererPdf(DummyBrevInnhold)
    }

    @Test
    fun `journalfører brev`() {
        val pdf = "".toByteArray()
        val sakId = UUID.randomUUID()
        val personMock = mock<PersonOppslag> {
            on { person(any()) } doReturn person.right()
        }
        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<BrevInnhold>()) } doReturn pdf.right()
        }
        val dokArkivMock = mock<DokArkiv> {
            on { opprettJournalpost(any()) } doReturn JournalpostId("journalpostId").right()
        }

        BrevServiceImpl(
            pdfGenerator = pdfGeneratorMock,
            personOppslag = personMock,
            dokArkiv = dokArkivMock,
            dokDistFordeling = mock()
        ).journalførBrev(DummyRequest, sakId) shouldBe JournalpostId("journalpostId").right()

        verify(personMock).person(DummyRequest.getFnr())
        verify(pdfGeneratorMock).genererPdf(DummyBrevInnhold)
        verify(dokArkivMock).opprettJournalpost(
            argThat { it shouldBe Journalpost.Vedtakspost(person, sakId.toString(), DummyBrevInnhold, pdf) }
        )
    }

    @Test
    fun `distribuerer brev`() {
        val dockdistMock = mock<DokDistFordeling> {
            on { bestillDistribusjon(JournalpostId("journalpostId")) } doReturn "en bestillings id".right()
        }

        BrevServiceImpl(
            pdfGenerator = mock(),
            personOppslag = mock(),
            dokArkiv = mock(),
            dokDistFordeling = dockdistMock
        ).distribuerBrev(JournalpostId("journalpostId")) shouldBe "en bestillings id".right()
        verify(dockdistMock).bestillDistribusjon(JournalpostId("journalpostId"))
    }
}

object DummyRequest : LagBrevRequest() {
    override fun getFnr(): Fnr = Fnr("12345678901")
    override fun lagBrevInnhold(personalia: BrevInnhold.Personalia) = DummyBrevInnhold
}

object DummyBrevInnhold : BrevInnhold() {
    override fun brevTemplate(): BrevTemplate = BrevTemplate.AvslagsVedtak
}
