package no.nav.su.se.bakover.dokument.infrastructure.database.journalføring

import arrow.core.right
import dokument.domain.journalføring.søknad.JournalførSøknadCommand
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.søknad.JournalførSøknadHttpClient
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.pdfATom
import no.nav.su.se.bakover.test.saksnummer
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import person.domain.Person
import java.util.UUID

internal class JournalførSøknadHttpClientTest {

    @ParameterizedTest
    @MethodSource("sakstypeProvider")
    fun `kan journalføre søknad`(
        sakstype: Sakstype,
        tittel: String,
        behandlingstema: String,
    ) {
        val mock = mock<no.nav.su.se.bakover.dokument.infrastructure.database.journalføring.JournalførHttpClient> {
            on { opprettJournalpost(any()) } doReturn JournalpostId("1").right()
        }
        val client = JournalførSøknadHttpClient(mock)
        val fnr = Fnr.generer()
        val internDokumentId = UUID.randomUUID()
        client.journalførSøknad(
            JournalførSøknadCommand(
                saksnummer = saksnummer,
                sakstype = sakstype,
                søknadInnholdJson = "{}",
                pdf = pdfATom(),
                datoDokument = fixedTidspunkt,
                fnr = fnr,
                navn = Person.Navn(fornavn = "Test", mellomnavn = "T.", etternavn = "Testesen"),
                internDokumentId = internDokumentId,
            ),
        ) shouldBe JournalpostId("1").right()
        verify(mock).opprettJournalpost(
            argThat {
                it shouldBe JournalførJsonRequest(
                    tittel = tittel,
                    journalpostType = JournalPostType.INNGAAENDE,
                    tema = "SUP",
                    kanal = "INNSENDT_NAV_ANSATT",
                    behandlingstema = behandlingstema,
                    journalfoerendeEnhet = "9999",
                    avsenderMottaker = AvsenderMottaker(id = fnr.toString()),
                    bruker = Bruker(id = fnr.toString()),
                    sak = Fagsak(saksnummer.toString()),
                    dokumenter = listOf(
                        JournalpostDokument(
                            tittel = tittel,
                            brevkode = "XX.YY-ZZ",
                            dokumentvarianter = listOf(
                                DokumentVariant.ArkivPDF(""),
                                DokumentVariant.OriginalJson("e30="),
                            ),
                        ),
                    ),
                    datoDokument = fixedTidspunkt,
                    eksternReferanseId = internDokumentId.toString(),
                )
            },
        )
    }

    companion object {
        @JvmStatic
        fun sakstypeProvider() = listOf(
            Arguments.of(Sakstype.UFØRE, "Søknad om supplerende stønad for uføre flyktninger", "ab0431"),
            Arguments.of(Sakstype.ALDER, "Søknad om supplerende stønad for alder", "ab0432"),
        )
    }
}
