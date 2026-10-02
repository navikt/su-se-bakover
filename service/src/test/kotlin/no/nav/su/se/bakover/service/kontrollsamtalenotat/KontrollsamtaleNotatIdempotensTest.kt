package no.nav.su.se.bakover.service.kontrollsamtalenotat

import arrow.core.left
import arrow.core.right
import dokument.domain.forsteside.PostForstesideResponse
import dokument.domain.journalføring.kontrollnotat.JournalførKontrollnotatClient
import dokument.domain.journalføring.kontrollnotat.JournalførKontrollnotatCommand
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.domain.Saksnummer
import no.nav.su.se.bakover.common.domain.client.ClientError
import no.nav.su.se.bakover.common.domain.sak.SakInfo
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollnotatPdfInnhold
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotat
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotatRepo
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.kontrollsamtale.application.kontrollnotat.KontrollsamtaleNotatServiceImpl
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleService
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtaler
import no.nav.su.se.bakover.kontrollsamtale.domain.kontrollnotat.KontrollsamtaleNotatService
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.kontrollsamtale.innkaltKontrollsamtale
import no.nav.su.se.bakover.test.kontrollsamtale.planlagtKontrollsamtale
import no.nav.su.se.bakover.test.person
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Duration
import java.util.UUID

internal class KontrollsamtaleNotatIdempotensTest {
    private val sakId = UUID.randomUUID()
    private val notatId = UUID.randomUUID()
    private val journalpostId = JournalpostId("journalpostId")
    private val fnr = Fnr.generer()
    private val sakInfo = SakInfo(sakId, Saksnummer(2021), fnr, Sakstype.UFØRE)
    private val notat = KontrollsamtaleNotat(
        id = notatId,
        sakId = sakId,
        opprettet = fixedTidspunkt,
        personligOppmøte = true,
        fullmaktOgLegeerklæring = null,
        originalPass = true,
        gyldigPass = true,
        harVærtUtenlands = false,
        utenlandsoppholdDatoer = emptyList(),
        harPlanerOmUtenlandsreise = false,
        planlagteUtenlandsreiseDatoer = emptyList(),
        reiseDokumentasjon = false,
        økonomiskSituasjon = false,
        andreForhold = false,
        skatteOpplysninger = false,
        fritekst = null,
    )
    private val lagredeNotater = mutableMapOf<UUID, KontrollsamtaleNotat>()
    private val repository = mock<KontrollsamtaleNotatRepo> {
        on { hentForId(any()) } doAnswer { lagredeNotater[it.getArgument<UUID>(0)] }
        on { lagre(any(), any()) } doAnswer {
            val innsendtNotat = it.getArgument<KontrollsamtaleNotat>(0)
            lagredeNotater.putIfAbsent(innsendtNotat.id, innsendtNotat) == null
        }
        on { oppdaterJournalpostId(any(), any()) } doAnswer {
            val id = it.getArgument<UUID>(0)
            val lagretNotat = requireNotNull(lagredeNotater[id])
            if (lagretNotat.journalpostId == null) {
                lagredeNotater[id] = lagretNotat.copy(journalpostId = it.getArgument<JournalpostId>(1))
                true
            } else {
                false
            }
        }
        on { hentUtenJournalpostId() } doAnswer { lagredeNotater.values.filter { it.journalpostId == null } }
    }
    private val journalførClient = mock<JournalførKontrollnotatClient> {
        on { journalførKontrollnotat(any()) } doReturn journalpostId.right()
    }
    private val oppgaveResponse = mock<OppgaveHttpKallResponse>()
    private val oppgaveService = mock<OppgaveService> {
        on { opprettOppgave(any()) } doReturn oppgaveResponse.right()
        on { opprettOppgaveMedSystembruker(any()) } doReturn oppgaveResponse.right()
    }
    private val kontrollsamtaleService = mock<KontrollsamtaleService> {
        on { hentKontrollsamtaler(sakId) } doReturn Kontrollsamtaler(sakId, emptyList())
    }

    private fun service(): KontrollsamtaleNotatServiceImpl {
        val pdfBytes = requireNotNull(javaClass.classLoader.getResourceAsStream("Foersteside.pdf")).use { it.readAllBytes() }
        val pdf = PdfA(pdfBytes)
        val forsteside = PostForstesideResponse(pdfBytes, "1234567890")
        val person = person(fnr = fnr)
        return KontrollsamtaleNotatServiceImpl(
            sakService = mock {
                on { hentSakInfo(sakId) } doReturn sakInfo.right()
            },
            personService = mock {
                on { hentPerson(any(), any()) } doReturn person.right()
                on { hentPersonMedSystembruker(any(), any()) } doReturn person.right()
            },
            repository = repository,
            pdfGenerator = mock {
                on { genererPdf(any<KontrollnotatPdfInnhold>()) } doReturn pdf.right()
            },
            forstesideGeneratorService = mock {
                on { genererForKontrollnotat(any(), any()) } doReturn forsteside.right()
            },
            clock = fixedClock,
            journalførKontrollnotatClient = journalførClient,
            oppgaveService = oppgaveService,
            kontrollsamtaleService = kontrollsamtaleService,
        )
    }

    @Test
    fun `gjentatt innsending og senere jobb gir bare ett journalføringskall og en oppgave`() {
        val service = service()
        val senereOpprettet = Tidspunkt.now(Clock.offset(fixedClock, Duration.ofSeconds(1)))
        val journalførtNotat = notat.copy(journalpostId = journalpostId)

        service.lagre(sakId, notat) shouldBe notat.right()
        service.lagre(sakId, notat.copy(opprettet = senereOpprettet)) shouldBe journalførtNotat.right()
        service.forsøkJournalpostPåNytt()

        repository.hentForId(notatId) shouldBe journalførtNotat
        verify(repository, times(1)).lagre(any(), any())
        verify(journalførClient, times(1)).journalførKontrollnotat(any())
        verify(oppgaveService, times(1)).opprettOppgave(any())
        verify(oppgaveService, never()).opprettOppgaveMedSystembruker(any())
    }

    @Test
    fun `gjentatt innsending etter journalføringsfeil lar jobben fullføre samme notat`() {
        val service = service()
        val journalføringsfeil = ClientError(500, "Feil ved journalføring")
        whenever(journalførClient.journalførKontrollnotat(any())).thenReturn(
            journalføringsfeil.left(),
            journalpostId.right(),
        )

        service.lagre(sakId, notat) shouldBe notat.right()
        service.lagre(sakId, notat) shouldBe notat.right()
        verify(oppgaveService, never()).opprettOppgave(any())

        service.forsøkJournalpostPåNytt()
        service.forsøkJournalpostPåNytt()

        repository.hentForId(notatId) shouldBe notat.copy(journalpostId = journalpostId)
        verify(repository, times(1)).lagre(any(), any())
        val journalføringCaptor = argumentCaptor<JournalførKontrollnotatCommand>()
        verify(journalførClient, times(2)).journalførKontrollnotat(journalføringCaptor.capture())
        journalføringCaptor.allValues.map { it.kontrollsamtaleNotatId } shouldBe listOf(notatId, notatId)
        journalføringCaptor.allValues.map { it.datoDokument } shouldBe listOf(notat.opprettet, notat.opprettet)
        verify(oppgaveService, times(1)).opprettOppgaveMedSystembruker(any())
    }

    @Test
    fun `samme ID returnerer eksisterende notat uten å endre innhold eller gjenta sideeffekter`() {
        val service = service()
        val endretNotat = notat.copy(fritekst = "Annet innhold")
        service.lagre(sakId, notat)

        service.lagre(sakId, endretNotat) shouldBe notat.copy(journalpostId = journalpostId).right()

        repository.hentForId(notatId) shouldBe notat.copy(journalpostId = journalpostId)
        verify(journalførClient, times(1)).journalførKontrollnotat(any())
        verify(oppgaveService, times(1)).opprettOppgave(any())
    }

    @Test
    fun `samme ID på annen sak avvises uten nye sideeffekter`() {
        val service = service()
        val annenSakId = UUID.randomUUID()
        service.lagre(sakId, notat)

        service.lagre(annenSakId, notat.copy(sakId = annenSakId)) shouldBe
            KontrollsamtaleNotatService.KontrollnotatIdAlleredeBrukt.left()

        repository.hentForId(notatId) shouldBe notat.copy(journalpostId = journalpostId)
        verify(journalførClient, times(1)).journalførKontrollnotat(any())
        verify(oppgaveService, times(1)).opprettOppgave(any())
    }

    @Test
    fun `annet POST-kall lagrer samme ID mellom oppslag og insert uten nye sideeffekter`() {
        val service = service()
        val eksisterendeNotat = notat.copy(journalpostId = journalpostId)
        doAnswer {
            lagredeNotater[notatId] = eksisterendeNotat
            false
        }.whenever(repository).lagre(notat, sakId)

        service.lagre(sakId, notat) shouldBe eksisterendeNotat.right()

        inOrder(repository) {
            verify(repository).hentForId(notatId)
            verify(repository).lagre(notat, sakId)
            verify(repository).hentForId(notatId)
        }
        lagredeNotater.values.toList() shouldBe listOf(eksisterendeNotat)
        verify(journalførClient, never()).journalførKontrollnotat(any())
        verify(repository, never()).oppdaterJournalpostId(any(), any())
        verify(oppgaveService, never()).opprettOppgave(any())
        verify(oppgaveService, never()).opprettOppgaveMedSystembruker(any())
    }

    @Test
    fun `konflikt ved insert returnerer eksisterende notat uten sideeffekter`() {
        val service = service()
        val eksisterendeNotat = notat.copy(fritekst = "Annet innhold")
        whenever(repository.hentForId(notatId)).thenReturn(null, eksisterendeNotat)
        doReturn(false).whenever(repository).lagre(notat, sakId)

        service.lagre(sakId, notat) shouldBe eksisterendeNotat.right()

        verify(journalførClient, never()).journalførKontrollnotat(any())
        verify(oppgaveService, never()).opprettOppgave(any())
    }

    @Test
    fun `planlagt kontrollsamtale gir journalføring men ingen oppgave ved gjentatt innsending`() {
        val kontrollsamtale = planlagtKontrollsamtale(sakId = sakId)
        whenever(kontrollsamtaleService.hentKontrollsamtaler(sakId)).thenReturn(Kontrollsamtaler(sakId, listOf(kontrollsamtale)))
        val service = service()

        service.lagre(sakId, notat)
        service.lagre(sakId, notat)
        service.forsøkJournalpostPåNytt()

        verify(journalførClient, times(1)).journalførKontrollnotat(any())
        verify(oppgaveService, never()).opprettOppgave(any())
        verify(oppgaveService, never()).opprettOppgaveMedSystembruker(any())
    }

    @Test
    fun `innkalt kontrollsamtale gir journalføring men ingen oppgave etter journalføringsfeil`() {
        val kontrollsamtale = innkaltKontrollsamtale(sakId = sakId)
        whenever(kontrollsamtaleService.hentKontrollsamtaler(sakId)).thenReturn(Kontrollsamtaler(sakId, listOf(kontrollsamtale)))
        val journalføringsfeil = ClientError(500, "Feil ved journalføring")
        whenever(journalførClient.journalførKontrollnotat(any())).thenReturn(
            journalføringsfeil.left(),
            journalpostId.right(),
        )
        val service = service()

        service.lagre(sakId, notat)
        service.forsøkJournalpostPåNytt()
        service.forsøkJournalpostPåNytt()

        repository.hentForId(notatId) shouldBe notat.copy(journalpostId = journalpostId)
        verify(journalførClient, times(2)).journalførKontrollnotat(any())
        verify(oppgaveService, never()).opprettOppgave(any())
        verify(oppgaveService, never()).opprettOppgaveMedSystembruker(any())
    }
}
