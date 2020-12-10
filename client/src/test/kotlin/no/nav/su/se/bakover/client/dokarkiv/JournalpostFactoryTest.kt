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
import org.junit.jupiter.api.Test
import java.util.Base64

internal class JournalpostFactoryTest {

    private val personMock = mock<Person>() {
        on { ident } doReturn Ident(Fnr("12345678910"), AktørId("12345"))
        on { navn } doReturn Person.Navn("fornavn", "mellomnavn", "etternavn")
    }
    private val saksnummer = Saksnummer(Math.random().toLong())
    private val pdf = "".toByteArray()

    @Test
    fun `lager vedtakspost for avslagsvedtak`() {
        val brevdata = mock<BrevInnhold> {
            on { brevTemplate } doReturn BrevTemplate.AvslagsVedtak
            on { toJson() } doReturn ""
        }
        JournalpostFactory.lagJournalpost(personMock, saksnummer, brevdata, pdf).let {
            it.shouldBeTypeOf<Journalpost.Vedtakspost>()
            assertVedtakspost(it, brevdata)
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
            assertVedtakspost(it, brevdata)
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
            assertInfopost(it, brevdata)
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
            assertVedtakspost(it, brevdata)
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
            assertInfopost(it, brevdata)
        }
    }

    private fun assertVedtakspost(journalpost: Journalpost, brevInnhold: BrevInnhold) =
        assertJournalpost(journalpost, brevInnhold, DokumentKategori.VB)

    private fun assertInfopost(journalpost: Journalpost, brevInnhold: BrevInnhold) =
        assertJournalpost(journalpost, brevInnhold, DokumentKategori.IB)

    private fun assertJournalpost(journalpost: Journalpost, brevInnhold: BrevInnhold, dokumentKategori: DokumentKategori) {
        journalpost.tittel shouldBe brevInnhold.brevTemplate.tittel()
        journalpost.avsenderMottaker shouldBe AvsenderMottaker(
            id = personMock.ident.fnr.toString(),
            navn = "${personMock.navn.etternavn}, ${personMock.navn.fornavn} ${personMock.navn.mellomnavn}"
        )
        journalpost.behandlingstema shouldBe "ab0268"
        journalpost.tema shouldBe "SUP"
        journalpost.bruker shouldBe Bruker(id = personMock.ident.fnr.toString())
        journalpost.kanal shouldBe null
        journalpost.journalfoerendeEnhet shouldBe "4815"
        journalpost.journalpostType shouldBe JournalPostType.UTGAAENDE
        journalpost.sak shouldBe Fagsak(saksnummer.nummer.toString())
        journalpost.dokumenter shouldBe listOf(
            JournalpostDokument(
                tittel = brevInnhold.brevTemplate.tittel(),
                dokumentKategori = dokumentKategori,
                dokumentvarianter = listOf(
                    DokumentVariant.ArkivPDF(fysiskDokument = Base64.getEncoder().encodeToString(pdf)),
                    DokumentVariant.OriginalJson(
                        fysiskDokument = Base64.getEncoder().encodeToString(brevInnhold.toJson().toByteArray()),
                    )
                )
            )
        )
    }
}
