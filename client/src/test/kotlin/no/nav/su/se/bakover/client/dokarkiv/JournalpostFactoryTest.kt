package no.nav.su.se.bakover.client.dokarkiv

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Sakstype
import no.nav.su.se.bakover.domain.brev.BrevInnhold
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.test.fixedTidspunkt
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.*
import kotlin.random.Random

internal class JournalpostFactoryTest {

    private val personMock = mock<Person> {
        on { ident } doReturn Ident(Fnr("12345678910"), AktørId("12345"))
        on { navn } doReturn Person.Navn("fornavn", "mellomnavn", "etternavn")
    }
    private val saksnummer = Saksnummer(Random.nextLong(2021, Long.MAX_VALUE))
    private val pdf = "".toByteArray()

    @Test
    fun `lager vedtakspost for vedtak dokumentkategori vedtak`() {
        val dokument = Dokument.MedMetadata.Vedtak(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            tittel = "tittel",
            generertDokument = "".toByteArray(),
            generertDokumentJson = """{"k":"v"}""",
            metadata = Dokument.Metadata(
                sakId = UUID.randomUUID(),
                bestillBrev = true,
            ),
        )

        JournalpostFactory.lagJournalpost(personMock, saksnummer, dokument, Sakstype.UFØRE).let {
            it.shouldBeTypeOf<Journalpost.Vedtakspost>()
            assert(it, dokument, DokumentKategori.VB)
        }
    }

    @Test
    fun `lager infopost for dokumentkategori informasjon`() {
        val dokument = Dokument.MedMetadata.Informasjon(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            tittel = "tittel",
            generertDokument = "".toByteArray(),
            generertDokumentJson = """{"k":"v"}""",
            metadata = Dokument.Metadata(
                sakId = UUID.randomUUID(),
                bestillBrev = true,
            ),
        )

        JournalpostFactory.lagJournalpost(personMock, saksnummer, dokument, Sakstype.UFØRE).let {
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
        tittel = dokument.tittel,
        originalJson = dokument.generertDokumentJson,
        dokumentKategori = dokumentKategori,
    )

    private fun assert(
        journalpost: Journalpost,
        brevInnhold: BrevInnhold,
        dokumentKategori: DokumentKategori,
    ) = assertJournalpost(
        journalpost = journalpost,
        tittel = brevInnhold.brevTemplate.tittel(),
        originalJson = brevInnhold.toJson(),
        dokumentKategori = dokumentKategori,
    )

    private fun assertJournalpost(
        journalpost: Journalpost,
        tittel: String,
        originalJson: String,
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
                        fysiskDokument = Base64.getEncoder().encodeToString(originalJson.toByteArray()),
                    ),
                ),
            ),
        )
    }
}
