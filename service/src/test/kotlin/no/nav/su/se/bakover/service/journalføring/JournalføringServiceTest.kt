package no.nav.su.se.bakover.service.journalføring

import arrow.core.right
import dokument.domain.Dokument
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.dokarkiv.DokArkiv
import no.nav.su.se.bakover.client.dokdistfordeling.DokDistFordeling
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.person.Ident
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.person.Person
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.sak.SakInfo
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.skatt.DokumentSkattRepo
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import no.nav.su.se.bakover.service.dokument.JournalførDokumentService
import no.nav.su.se.bakover.service.skatt.JournalførSkattDokumentService
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.pdfATom
import no.nav.su.se.bakover.test.sakinfo
import no.nav.su.se.bakover.test.skatt.nySkattedokumentGenerert
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.Clock
import java.time.Year
import java.util.UUID

class JournalføringServiceTest {
    val fnr = sakinfo.fnr
    val person = Person(
        ident = Ident(
            fnr = sakinfo.fnr,
            aktørId = AktørId(aktørId = "123"),
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = null, etternavn = "Strømøy"),
        fødsel = Person.Fødsel.MedFødselsår(
            år = Year.of(1956),
        ),
    )

    @Test
    fun `journalfører dokumenter`() {
        val dokumentDis = dokumentdistribusjon()
        val skattDokument = nySkattedokumentGenerert(sakId = sakinfo.sakId)

        val sakService = mock<SakService> {
            on { hentSakInfo(any()) } doReturn sakinfo.right()
        }
        val personService = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }
        val dokumentRepo = mock<DokumentRepo> {
            on { this.hentDokumenterForJournalføring() } doReturn listOf(dokumentDis)
        }
        val dokumentSkattRepo = mock<DokumentSkattRepo> {
            on { this.hentDokumenterForJournalføring() } doReturn listOf(skattDokument)
        }
        val dokarkiv = mock<DokArkiv> {
            on { opprettJournalpost(any()) } doReturn JournalpostId("1").right()
        }

        val serviceAndMocks = ServiceOgMocks(
            sakService = sakService,
            personService = personService,
            dokumentRepo = dokumentRepo,
            dokumentSkattRepo = dokumentSkattRepo,
            dokArkiv = dokarkiv,
        )
        serviceAndMocks.journalføringService.journalfør()
        verify(dokumentRepo, times(1)).hentDokumenterForJournalføring()
        verify(dokumentSkattRepo, times(1)).hentDokumenterForJournalføring()
        verify(sakService, times(2)).hentSakInfo(argThat { it shouldBe sakinfo.sakId })
        verify(personService, times(2)).hentPersonMedSystembruker(argThat { it shouldBe fnr })
        verify(dokumentRepo, times(1)).oppdaterDokumentdistribusjon(
            argThat {
                it shouldBe dokumentDis.copy(
                    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.Journalført(JournalpostId("1")),
                )
            },
        )
        verify(dokumentSkattRepo, times(1)).lagre(
            argThat { it shouldBe Skattedokument.Journalført(skattDokument, JournalpostId("1")) },
        )
        verifyNoMoreInteractions(sakService, personService, dokumentRepo, dokumentSkattRepo)
    }

    private data class ServiceOgMocks(
        val dokArkiv: DokArkiv = mock(),
        val dokDistFordeling: DokDistFordeling = mock(),
        val dokumentRepo: DokumentRepo = mock(),
        val dokumentSkattRepo: DokumentSkattRepo = mock(),
        val sakService: SakService = mock(),
        val personService: PersonService = mock(),
        val clock: Clock = mock(),
        val sakInfo: SakInfo = sakinfo,
    ) {
        val journalføringService = JournalføringService(
            journalførDokumentService = JournalførDokumentService(
                dokArkiv = dokArkiv,
                dokumentRepo = dokumentRepo,
                sakService = sakService,
                personService = personService,
            ),
            journalførSkattDokumentService = JournalførSkattDokumentService(
                dokArkiv = dokArkiv,
                sakService = sakService,
                personService = personService,
                dokumentSkattRepo = dokumentSkattRepo,
            ),
        )
    }
}

private fun dokumentdistribusjon(): Dokumentdistribusjon = Dokumentdistribusjon(
    id = UUID.randomUUID(),
    opprettet = fixedTidspunkt,
    dokument = Dokument.MedMetadata.Vedtak(
        id = UUID.randomUUID(),
        opprettet = fixedTidspunkt,
        tittel = "tittel",
        generertDokument = pdfATom(),
        generertDokumentJson = "{}",
        metadata = Dokument.Metadata(sakId = sakinfo.sakId),
    ),
    journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
)
