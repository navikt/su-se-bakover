package no.nav.su.se.bakover.service.brev

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
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
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.BrevTemplate
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.service.argThat
import org.junit.jupiter.api.Test

internal class BrevServiceImplTest {

    private companion object {
        private val fnr = Fnr(fnr = "12345678901")
        private val person = Person(
            ident = Ident(
                fnr = fnr,
                aktørId = AktørId(aktørId = "123")
            ),
            navn = Person.Navn(fornavn = "Tore", mellomnavn = null, etternavn = "Strømøy")
        )
    }

    @Test
    fun `lager brev`() {
        val pdf = "".toByteArray()

        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<BrevInnhold>()) } doReturn pdf.right()
        }

        BrevServiceImpl(
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = mock(),
            dokDistFordeling = mock()
        ).lagBrev(DummyRequest) shouldBe pdf.right()

        verify(pdfGeneratorMock).genererPdf(DummyBrevInnhold)

        verifyNoMoreInteractions(pdfGeneratorMock)
    }

    @Test
    fun `lager ikke brev når pdf-generator kall failer`() {

        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(DummyBrevInnhold) } doReturn KunneIkkeGenererePdf.left()
        }

        BrevServiceImpl(
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = mock(),
            dokDistFordeling = mock()
        ).lagBrev(DummyRequest) shouldBe KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        verify(pdfGeneratorMock).genererPdf(DummyBrevInnhold)
        verifyNoMoreInteractions(pdfGeneratorMock)
    }

    @Test
    fun `journalfører brev`() {
        val pdf = "".toByteArray()
        val saksnummer = Saksnummer(1337)

        val pdfGeneratorMock = mock<PdfGenerator> {
            on { genererPdf(any<BrevInnhold>()) } doReturn pdf.right()
        }
        val dokArkivMock = mock<DokArkiv> {
            on { opprettJournalpost(any()) } doReturn JournalpostId("journalpostId").right()
        }

        BrevServiceImpl(
            pdfGenerator = pdfGeneratorMock,
            dokArkiv = dokArkivMock,
            dokDistFordeling = mock()
        ).journalførBrev(DummyRequest, saksnummer) shouldBe JournalpostId("journalpostId").right()

        verify(pdfGeneratorMock).genererPdf(DummyBrevInnhold)
        verify(dokArkivMock).opprettJournalpost(
            argThat { it shouldBe Journalpost.Vedtakspost(person, saksnummer, DummyBrevInnhold, pdf) }
        )
        verifyNoMoreInteractions(pdfGeneratorMock, dokArkivMock)
    }

    @Test
    fun `distribuerer brev`() {
        val dockdistMock = mock<DokDistFordeling> {
            on { bestillDistribusjon(JournalpostId("journalpostId")) } doReturn BrevbestillingId("en bestillings id").right()
        }

        BrevServiceImpl(
            pdfGenerator = mock(),
            dokArkiv = mock(),
            dokDistFordeling = dockdistMock
        ).distribuerBrev(JournalpostId("journalpostId")) shouldBe BrevbestillingId("en bestillings id").right()
        verify(dockdistMock).bestillDistribusjon(JournalpostId("journalpostId"))
    }

    object DummyRequest : LagBrevRequest {
        override fun getPerson(): Person = person
        override fun lagBrevInnhold(personalia: BrevInnhold.Personalia) = DummyBrevInnhold
    }

    object DummyBrevInnhold : BrevInnhold() {
        override val brevTemplate: BrevTemplate = BrevTemplate.AvslagsVedtak
    }
}
