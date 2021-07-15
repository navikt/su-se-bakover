package no.nav.su.se.bakover.client.dokarkiv

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.brev.BrevTemplate
import no.nav.su.se.bakover.domain.dokument.Dokument
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.UUID
import kotlin.random.Random

internal class JournalpostFactoryTest {

    private val personMock = mock<Person>() {
        on { ident } doReturn Ident(Fnr("12345678910"), AktørId("12345"))
        on { navn } doReturn Person.Navn("fornavn", "mellomnavn", "etternavn")
    }
    private val saksnummer = Saksnummer(Random.nextLong(2021, Long.MAX_VALUE))
    private val pdf = "".toByteArray()

    @Test
    fun `lager vedtakspost for avslagsvedtak`() {
        val brevdata = mock<BrevInnhold> {
            on { brevTemplate } doReturn BrevTemplate.AvslagsVedtak
            on { toJson() } doReturn ""
        }
        JournalpostFactory.lagJournalpost(personMock, saksnummer, brevdata, pdf).let {
            it.shouldBeTypeOf<Journalpost.Vedtakspost>()
            assert(it, brevdata, DokumentKategori.VB)
        }
    }

    @Test
    fun `lager vedtakspost for innvilget vedtak`() {
        val brevdata = mock<BrevInnhold> {
            on { brevTemplate } doReturn BrevTemplate.InnvilgetVedtak
            on { toJson() } doReturn ""
        }

        JournalpostFactory.lagJournalpost(personMock, saksnummer, brevdata, pdf).let {
            it.shouldBeTypeOf<Journalpost.Vedtakspost>()
            assert(it, brevdata, DokumentKategori.VB)
        }
    }

    @Test
    fun `lager journalpost for en trukket søknad`() {
        val brevdata = mock<BrevInnhold>() {
            on { brevTemplate } doReturn BrevTemplate.TrukketSøknad
            on { toJson() } doReturn ""
        }
        JournalpostFactory.lagJournalpost(personMock, saksnummer, brevdata, pdf).let {
            it.shouldBeTypeOf<Journalpost.Info>()
            assert(it, brevdata, DokumentKategori.IB)
        }
    }

    @Test
    fun `lager journalpost for en avvist søknad med vedtak`() {
        val brevdata = mock<BrevInnhold>() {
            on { brevTemplate } doReturn BrevTemplate.AvvistSøknadVedtak
            on { toJson() } doReturn ""
        }
        JournalpostFactory.lagJournalpost(personMock, saksnummer, brevdata, pdf).let {
            it.shouldBeTypeOf<Journalpost.Vedtakspost>()
            assert(it, brevdata, DokumentKategori.VB)
        }
    }

    @Test
    fun `lager journalpost for en avvist søknad med fritekst`() {
        val brevdata = mock<BrevInnhold>() {
            on { brevTemplate } doReturn BrevTemplate.AvvistSøknadFritekst
            on { toJson() } doReturn ""
        }

        JournalpostFactory.lagJournalpost(personMock, saksnummer, brevdata, pdf).let {
            it.shouldBeTypeOf<Journalpost.Info>()
            assert(it, brevdata, DokumentKategori.IB)
        }
    }

    @Test
    fun `lager vedtakspost for revurdering av inntekt`() {
        val brevdata = mock<BrevInnhold>() {
            on { brevTemplate } doReturn BrevTemplate.Revurdering.Inntekt
            on { toJson() } doReturn ""
        }

        JournalpostFactory.lagJournalpost(personMock, saksnummer, brevdata, pdf).let {
            it.shouldBeTypeOf<Journalpost.Vedtakspost>()
            assert(it, brevdata, DokumentKategori.VB)
        }
    }

    @Test
    fun `lager vedtakspost for opphørsvedtak`() {
        val brevdata = mock<BrevInnhold>() {
            on { brevTemplate } doReturn BrevTemplate.Opphørsvedtak
            on { toJson() } doReturn ""
        }

        JournalpostFactory.lagJournalpost(personMock, saksnummer, brevdata, pdf).let {
            it.shouldBeTypeOf<Journalpost.Vedtakspost>()
            assert(it, brevdata, DokumentKategori.VB)
        }
    }

    @Test
    fun `lager vedtakspost for vedtak ingen endring`() {
        val brevdata = mock<BrevInnhold>() {
            on { brevTemplate } doReturn BrevTemplate.VedtakIngenEndring
            on { toJson() } doReturn ""
        }

        JournalpostFactory.lagJournalpost(personMock, saksnummer, brevdata, pdf).let {
            it.shouldBeTypeOf<Journalpost.Vedtakspost>()
            assert(it, brevdata, DokumentKategori.VB)
        }
    }

    @Test
    fun `lager vedtakspost for vedtak dokumentkategori vedtak`() {
        val dokument = Dokument.Vedtak(
            generertDokument = "".toByteArray(),
            generertDokumentJson = """{"k":"v"}""",
            metadata = Dokument.Metadata(
                sakId = UUID.randomUUID(),
                tittel = "tittel",
                bestillBrev = true,
            ),
        )

        JournalpostFactory.lagJournalpost(personMock, saksnummer, dokument).let {
            it.shouldBeTypeOf<Journalpost.Vedtakspost>()
            assert(it, dokument, DokumentKategori.VB)
        }
    }

    @Test
    fun `lager infopost for dokumentkategori informasjon`() {
        val dokument = Dokument.Informasjon(
            generertDokument = "".toByteArray(),
            generertDokumentJson = """{"k":"v"}""",
            metadata = Dokument.Metadata(
                sakId = UUID.randomUUID(),
                tittel = "tittel",
                bestillBrev = true,
            ),
        )

        JournalpostFactory.lagJournalpost(personMock, saksnummer, dokument).let {
            it.shouldBeTypeOf<Journalpost.Info>()
            assert(it, dokument, DokumentKategori.IB)
        }
    }

    private fun assert(
        journalpost: Journalpost,
        dokument: Dokument,
        dokumentKategori: DokumentKategori,
    ) = assertJournalpost(
        journalpost = journalpost,
        tittel = dokument.metadata.tittel,
        pdfRequestJson = dokument.generertDokumentJson,
        dokumentKategori = dokumentKategori,
    )

    private fun assert(
        journalpost: Journalpost,
        brevInnhold: BrevInnhold,
        dokumentKategori: DokumentKategori,
    ) = assertJournalpost(
        journalpost = journalpost,
        tittel = brevInnhold.brevTemplate.tittel(),
        pdfRequestJson = brevInnhold.toJson(),
        dokumentKategori = dokumentKategori,
    )

    private fun assertJournalpost(
        journalpost: Journalpost,
        tittel: String,
        pdfRequestJson: String,
        dokumentKategori: DokumentKategori,
    ) {
        journalpost.tittel shouldBe tittel
        journalpost.avsenderMottaker shouldBe AvsenderMottaker(
            id = personMock.ident.fnr.toString(),
            navn = "${personMock.navn.etternavn}, ${personMock.navn.fornavn} ${personMock.navn.mellomnavn}",
        )
        journalpost.behandlingstema shouldBe "ab0431"
        journalpost.tema shouldBe "SUP"
        journalpost.bruker shouldBe Bruker(id = personMock.ident.fnr.toString())
        journalpost.kanal shouldBe null
        journalpost.journalfoerendeEnhet shouldBe "4815"
        journalpost.journalpostType shouldBe JournalPostType.UTGAAENDE
        journalpost.sak shouldBe Fagsak(saksnummer.nummer.toString())
        journalpost.dokumenter shouldBe listOf(
            JournalpostDokument(
                tittel = tittel,
                dokumentKategori = dokumentKategori,
                dokumentvarianter = listOf(
                    DokumentVariant.ArkivPDF(fysiskDokument = Base64.getEncoder().encodeToString(pdf)),
                    DokumentVariant.OriginalJson(
                        fysiskDokument = Base64.getEncoder().encodeToString(pdfRequestJson.toByteArray()),
                    ),
                ),
            ),
        )
    }
}
