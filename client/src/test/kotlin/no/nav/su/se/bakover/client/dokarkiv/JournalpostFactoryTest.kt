package no.nav.su.se.bakover.client.dokarkiv

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.beOfType
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.brev.BrevTemplate
import no.nav.su.se.bakover.domain.brev.Brevdata
import org.junit.jupiter.api.Test
import java.util.Base64
import java.util.UUID

internal class JournalpostFactoryTest {

    private val personMock = mock<Person>() {
        on { ident } doReturn Ident(Fnr("12345678910"), AktørId("12345"))
        on { navn } doReturn Person.Navn("fornavn", "mellomnavn", "etternavn")
    }
    private val sakId = UUID.randomUUID()
    private val pdf = "".toByteArray()

    @Test
    fun `lager vedtakspost for avslagsvedtak`() {
        val brevdata = mock<Brevdata>() {
            on { brevtype() } doReturn BrevTemplate.AvslagsVedtak
            on { toJson() } doReturn ""
        }
        JournalpostFactory.lagJournalpost(personMock, sakId, brevdata, pdf).let {
            it should beOfType<Journalpost.Vedtakspost>()
            assertVedtakspost(it, brevdata)
        }
    }

    @Test
    fun `lager vedtakspost for innvilget vedtak`() {
        val brevdata = mock<Brevdata>() {
            on { brevtype() } doReturn BrevTemplate.InnvilgetVedtak
            on { toJson() } doReturn ""
        }

        JournalpostFactory.lagJournalpost(personMock, sakId, brevdata, pdf).let {
            it should beOfType<Journalpost.Vedtakspost>()
            assertVedtakspost(it, brevdata)
        }
    }

    @Test
    fun `lager journalpost for en trukket søknad`() {
        val brevdata = mock<Brevdata>() {
            on { brevtype() } doReturn BrevTemplate.TrukketSøknad
            on { toJson() } doReturn ""
        }
        JournalpostFactory.lagJournalpost(personMock, sakId, brevdata, pdf).let {
            it should beOfType<Journalpost.Info>()
            assertInfopost(it, brevdata)
        }
    }

    @Test
    fun `lager journalpost for en avvist søknad med vedtak`() {
        val brevdata = mock<Brevdata>() {
            on { brevtype() } doReturn BrevTemplate.AvvistSøknadVedtak
            on { toJson() } doReturn ""
        }
        JournalpostFactory.lagJournalpost(personMock, sakId, brevdata, pdf).let {
            it should beOfType<Journalpost.Vedtakspost>()
            assertVedtakspost(it, brevdata)
        }
    }

    @Test
    fun `lager journalpost for en avvist søknad med fritekst`() {
        val brevdata = mock<Brevdata>() {
            on { brevtype() } doReturn BrevTemplate.AvvistSøknadFritekst
            on { toJson() } doReturn ""
        }

        JournalpostFactory.lagJournalpost(personMock, sakId, brevdata, pdf).let {
            it should beOfType<Journalpost.Info>()
            assertInfopost(it, brevdata)
        }
    }

    private fun assertVedtakspost(journalpost: Journalpost, brevdata: Brevdata) =
        assertJournalpost(journalpost, brevdata, DokumentKategori.VB)

    private fun assertInfopost(journalpost: Journalpost, brevdata: Brevdata) =
        assertJournalpost(journalpost, brevdata, DokumentKategori.IB)

    private fun assertJournalpost(journalpost: Journalpost, brevdata: Brevdata, dokumentKategori: DokumentKategori) {
        journalpost.tittel shouldBe brevdata.brevtype().tittel()
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
        journalpost.sak shouldBe Fagsak(sakId.toString())
        journalpost.dokumenter shouldBe listOf(
            JournalpostDokument(
                tittel = brevdata.brevtype().tittel(),
                dokumentKategori = dokumentKategori,
                dokumentvarianter = listOf(
                    DokumentVariant.ArkivPDF(fysiskDokument = Base64.getEncoder().encodeToString(pdf)),
                    DokumentVariant.OriginalJson(
                        fysiskDokument = Base64.getEncoder().encodeToString(brevdata.toJson().toByteArray()),
                    )
                )
            )
        )
    }
}
