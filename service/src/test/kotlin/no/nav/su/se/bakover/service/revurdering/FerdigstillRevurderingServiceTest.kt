package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doReturnConsecutively
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.client.person.MicrosoftGraphResponse
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknadsbehandling.FerdigstillIverksettingServiceImpl
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class FerdigstillRevurderingServiceTest {

    private val iverksattJournalpostId = JournalpostId("iverksattJournalpostId")
    private val iverksattBrevbestillingId = BrevbestillingId("iverattBrevbestillingId")
    private val utbetalingId = UUID30.randomUUID()
    private val sakId: UUID = UUID.randomUUID()
    private val saksnummer = Saksnummer(1)
    private val søknadId: UUID = UUID.randomUUID()
    private val fnr = FnrGenerator.random()

    private val søknadsbehandlingTilRevurdering = Søknadsbehandling.Iverksatt.Innvilget(
        id = UUID.randomUUID(),
        sakId = sakId,
        opprettet = Tidspunkt.EPOCH,
        saksbehandler = NavIdentBruker.Saksbehandler("s11"),
        attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("a22")),
        oppgaveId = OppgaveId("23"),
        saksnummer = saksnummer,
        søknad = Søknad.Journalført.MedOppgave(
            id = søknadId,
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = OppgaveId("56"),
            journalpostId = JournalpostId("315")
        ),
        beregning = TestBeregning,
        simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "",
            datoBeregnet = LocalDate.EPOCH,
            nettoBeløp = 0,
            periodeList = listOf()
        ),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
        fnr = fnr,
        utbetalingId = UUID30.randomUUID(),
        eksterneIverksettingsteg = EksterneIverksettingsstegEtterUtbetaling.JournalførtOgDistribuertBrev(
            journalpostId = JournalpostId("515"),
            brevbestillingId = BrevbestillingId("551")
        )
    )

    private val revurderingSomSkalFerdigstilles = IverksattRevurdering(
        id = UUID.randomUUID(),
        periode = Periode.create(1.januar(2021), 31.mars(2021)),
        opprettet = Tidspunkt.EPOCH,
        tilRevurdering = søknadsbehandlingTilRevurdering,
        saksbehandler = NavIdentBruker.Saksbehandler("Z123"),
        beregning = TestBeregning,
        simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "",
            datoBeregnet = LocalDate.EPOCH,
            nettoBeløp = 0,
            periodeList = listOf()
        ),
        oppgaveId = OppgaveId(""),
        attestant = NavIdentBruker.Attestant(navIdent = "Z321"),
        utbetalingId = utbetalingId,
        eksterneIverksettingsteg = EksterneIverksettingsstegEtterUtbetaling.VenterPåKvittering
    )

    private val person = Person(
        ident = Ident(
            fnr = fnr,
            aktørId = AktørId(aktørId = "123")
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy")
    )

    private object microsoftGraphMock {
        val response = MicrosoftGraphResponse(
            onPremisesSamAccountName = "",
            displayName = "Nav Navesen",
            givenName = "",
            mail = "",
            officeLocation = "",
            surname = "",
            userPrincipalName = "",
            id = "",
            jobTitle = ""
        )
    }

    @Test
    fun `kaster exception hvis vi ikke finner person ved opprettelse av brev`() {
        val revurderingRepoMock = mock<RevurderingRepo>() {
            on { hentRevurderingForUtbetaling(any()) } doReturn revurderingSomSkalFerdigstilles
        }
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }
        assertThrows<FerdigstillRevurderingService.KunneIkkeLageBrevRequestException> {
            createFerdigstillIverksettingService(
                revurderingRepo = revurderingRepoMock,
                personService = personServiceMock
            ).ferdigstillIverksetting(utbetalingId)
        }

        inOrder(
            revurderingRepoMock,
            personServiceMock
        ) {
            verify(revurderingRepoMock).hentRevurderingForUtbetaling(utbetalingId)
            verify(personServiceMock).hentPersonMedSystembruker(revurderingSomSkalFerdigstilles.fnr)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kaster exception hvis vi ikke finner navn på saksbehandler ved opprettelse av brev`() {
        val revurderingRepoMock = mock<RevurderingRepo>() {
            on { hentRevurderingForUtbetaling(any()) } doReturn revurderingSomSkalFerdigstilles
        }
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }
        val hentNavnMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn MicrosoftGraphApiOppslagFeil.DeserialiseringAvResponsFeilet.left()
        }

        assertThrows<FerdigstillRevurderingService.KunneIkkeLageBrevRequestException> {
            createFerdigstillIverksettingService(
                revurderingRepo = revurderingRepoMock,
                personService = personServiceMock,
                microsoftGraphApiOppslag = hentNavnMock
            ).ferdigstillIverksetting(utbetalingId)
        }

        inOrder(
            revurderingRepoMock,
            personServiceMock,
            hentNavnMock
        ) {
            verify(revurderingRepoMock).hentRevurderingForUtbetaling(utbetalingId)
            verify(personServiceMock).hentPersonMedSystembruker(revurderingSomSkalFerdigstilles.fnr)
            verify(hentNavnMock).hentBrukerinformasjonForNavIdent(revurderingSomSkalFerdigstilles.saksbehandler)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kaster exception hvis vi ikke finner navn på attestant ved opprettelse av brev`() {
        val revurderingRepoMock = mock<RevurderingRepo>() {
            on { hentRevurderingForUtbetaling(any()) } doReturn revurderingSomSkalFerdigstilles
        }
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }
        val hentNavnMock = mock<MicrosoftGraphApiOppslag> {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturnConsecutively listOf(
                microsoftGraphMock.response.right(),
                MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
            )
        }

        assertThrows<FerdigstillRevurderingService.KunneIkkeLageBrevRequestException> {
            createFerdigstillIverksettingService(
                revurderingRepo = revurderingRepoMock,
                personService = personServiceMock,
                microsoftGraphApiOppslag = hentNavnMock
            ).ferdigstillIverksetting(utbetalingId)
        }

        inOrder(
            revurderingRepoMock,
            personServiceMock,
            hentNavnMock
        ) {
            verify(revurderingRepoMock).hentRevurderingForUtbetaling(utbetalingId)
            verify(personServiceMock).hentPersonMedSystembruker(revurderingSomSkalFerdigstilles.fnr)
            verify(hentNavnMock).hentBrukerinformasjonForNavIdent(revurderingSomSkalFerdigstilles.saksbehandler)
            verify(hentNavnMock).hentBrukerinformasjonForNavIdent(revurderingSomSkalFerdigstilles.attestant)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kaster exception hvis journalføring feiler av tekniske årsaker`() {
        val revurderingRepoMock = mock<RevurderingRepo>() {
            on { hentRevurderingForUtbetaling(any()) } doReturn revurderingSomSkalFerdigstilles
        }
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }
        val hentNavnMock = mock<MicrosoftGraphApiOppslag> {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn microsoftGraphMock.response.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost.left()
        }

        assertThrows<FerdigstillRevurderingService.KunneIkkeJournalføreBrevException> {
            createFerdigstillIverksettingService(
                revurderingRepo = revurderingRepoMock,
                personService = personServiceMock,
                microsoftGraphApiOppslag = hentNavnMock,
                brevService = brevServiceMock
            ).ferdigstillIverksetting(utbetalingId)
        }

        inOrder(
            revurderingRepoMock,
            personServiceMock,
            hentNavnMock,
            brevServiceMock
        ) {
            verify(revurderingRepoMock).hentRevurderingForUtbetaling(utbetalingId)
            verify(personServiceMock).hentPersonMedSystembruker(revurderingSomSkalFerdigstilles.fnr)
            verify(hentNavnMock).hentBrukerinformasjonForNavIdent(revurderingSomSkalFerdigstilles.saksbehandler)
            verify(hentNavnMock).hentBrukerinformasjonForNavIdent(revurderingSomSkalFerdigstilles.attestant)
            verify(brevServiceMock).journalførBrev(
                argThat { it.shouldBeTypeOf<LagBrevRequest.Revurdering.Inntekt>() },
                argThat { it shouldBe saksnummer }
            )
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kaster exception hvis brevdistribusjon feiler av tekniske årsaker`() {
        val revurderingRepoMock = mock<RevurderingRepo>() {
            on { hentRevurderingForUtbetaling(any()) } doReturn revurderingSomSkalFerdigstilles
        }
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }
        val hentNavnMock = mock<MicrosoftGraphApiOppslag> {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn microsoftGraphMock.response.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn iverksattJournalpostId.right()
            on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
        }

        assertThrows<FerdigstillRevurderingService.KunneIkkeDistribuereBrevException> {
            createFerdigstillIverksettingService(
                revurderingRepo = revurderingRepoMock,
                personService = personServiceMock,
                microsoftGraphApiOppslag = hentNavnMock,
                brevService = brevServiceMock
            ).ferdigstillIverksetting(utbetalingId)
        }

        inOrder(
            revurderingRepoMock,
            personServiceMock,
            hentNavnMock,
            brevServiceMock
        ) {
            verify(revurderingRepoMock).hentRevurderingForUtbetaling(utbetalingId)
            verify(personServiceMock).hentPersonMedSystembruker(revurderingSomSkalFerdigstilles.fnr)
            verify(hentNavnMock).hentBrukerinformasjonForNavIdent(revurderingSomSkalFerdigstilles.saksbehandler)
            verify(hentNavnMock).hentBrukerinformasjonForNavIdent(revurderingSomSkalFerdigstilles.attestant)
            verify(brevServiceMock).journalførBrev(
                argThat { it.shouldBeTypeOf<LagBrevRequest.Revurdering.Inntekt>() },
                argThat { it shouldBe saksnummer }
            )
            verify(revurderingRepoMock).lagre(
                revurderingSomSkalFerdigstilles.copy(
                    eksterneIverksettingsteg = EksterneIverksettingsstegEtterUtbetaling.Journalført(
                        iverksattJournalpostId
                    )
                )
            )
            verify(brevServiceMock).distribuerBrev(iverksattJournalpostId)
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `overlever hvis lukking av oppgave feiler`() {
        val revurderingRepoMock = mock<RevurderingRepo>() {
            on { hentRevurderingForUtbetaling(any()) } doReturn revurderingSomSkalFerdigstilles
        }
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }
        val hentNavnMock = mock<MicrosoftGraphApiOppslag> {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn microsoftGraphMock.response.right()
        }
        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn iverksattJournalpostId.right()
            on { distribuerBrev(any()) } doReturn iverksattBrevbestillingId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgaveMedSystembruker(any()) } doReturn KunneIkkeLukkeOppgave.left()
        }

        createFerdigstillIverksettingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = hentNavnMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock
        ).ferdigstillIverksetting(utbetalingId)

        inOrder(
            revurderingRepoMock,
            personServiceMock,
            hentNavnMock,
            brevServiceMock,
            oppgaveServiceMock
        ) {
            verify(revurderingRepoMock).hentRevurderingForUtbetaling(utbetalingId)
            verify(personServiceMock).hentPersonMedSystembruker(revurderingSomSkalFerdigstilles.fnr)
            verify(hentNavnMock).hentBrukerinformasjonForNavIdent(revurderingSomSkalFerdigstilles.saksbehandler)
            verify(hentNavnMock).hentBrukerinformasjonForNavIdent(revurderingSomSkalFerdigstilles.attestant)
            verify(brevServiceMock).journalførBrev(
                argThat { it.shouldBeTypeOf<LagBrevRequest.Revurdering.Inntekt>() },
                argThat { it shouldBe saksnummer }
            )
            verify(revurderingRepoMock).lagre(
                revurderingSomSkalFerdigstilles.copy(
                    eksterneIverksettingsteg = EksterneIverksettingsstegEtterUtbetaling.Journalført(
                        iverksattJournalpostId
                    )
                )
            )
            verify(brevServiceMock).distribuerBrev(iverksattJournalpostId)
            verify(revurderingRepoMock).lagre(
                revurderingSomSkalFerdigstilles.copy(
                    eksterneIverksettingsteg = EksterneIverksettingsstegEtterUtbetaling.JournalførtOgDistribuertBrev(
                        iverksattJournalpostId,
                        iverksattBrevbestillingId
                    )
                )
            )
            verify(oppgaveServiceMock).lukkOppgaveMedSystembruker(revurderingSomSkalFerdigstilles.oppgaveId)
            verifyNoMoreInteractions()
        }
    }

    private fun createFerdigstillIverksettingService(
        søknadsbehandlingRepo: SøknadsbehandlingRepo = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
        microsoftGraphApiOppslag: MicrosoftGraphApiOppslag = mock(),
        brevService: BrevService = mock(),
        eventObserver: EventObserver = mock { on { handle(any()) }.doNothing() },
        clock: Clock = Clock.systemUTC(),
        revurderingRepo: RevurderingRepo = mock()
    ) = FerdigstillIverksettingServiceImpl(
        søknadsbehandlingRepo = søknadsbehandlingRepo,
        oppgaveService = oppgaveService,
        behandlingMetrics = behandlingMetrics,
        microsoftGraphApiClient = microsoftGraphApiOppslag,
        personService = personService,
        brevService = brevService,
        clock = clock,
        revurderingRepo
    ).apply { addObserver(eventObserver) }
}
