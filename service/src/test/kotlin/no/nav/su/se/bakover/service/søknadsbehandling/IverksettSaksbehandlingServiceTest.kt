package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.client.person.MicrosoftGraphResponse
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.database.SaksbehandlingRepo
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Statusovergang
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagBrevRequest
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.behandling.DistribuerIverksettingsbrevService
import no.nav.su.se.bakover.service.behandling.IverksettSaksbehandlingService
import no.nav.su.se.bakover.service.behandling.JournalførIverksettingService
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.utbetaling.KunneIkkeUtbetale
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.snapshot.OpprettVedtakssnapshotService
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID

internal class IverksettSaksbehandlingServiceTest {
    private val fnr = FnrGenerator.random()
    private val sakId: UUID = UUID.fromString("268e62fb-3079-4e8d-ab32-ff9fb9eac2ec")
    private val behandlingId: UUID = UUID.fromString("a602aa68-c989-43e3-9fb7-cb488a2a3821")
    private val saksnummer = Saksnummer(999999)
    private val oppgaveId = OppgaveId("o")
    private val iverksattJournalpostId = JournalpostId("j")
    private val iverksattBrevbestillingId = BrevbestillingId("2")
    private val søknadOppgaveId = OppgaveId("søknadOppgaveId")
    private val attestant = NavIdentBruker.Attestant("attestant")
    private val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlinger")
    private val utbetalingId = UUID30.randomUUID()
    private val person = Person(
        ident = Ident(
            fnr = BehandlingTestUtils.fnr,
            aktørId = AktørId(aktørId = "123")
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy")
    )
    private val graphApiResponse = MicrosoftGraphResponse(
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

    @Test
    fun `svarer med feil hvis man ikke finner person for journalpost ved avslag`() {
        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val response = createService(
            personService = personServiceMock,
        ).opprettJournalpostForAvslag(avslagTilAttestering(), attestant)

        response shouldBe Statusovergang.KunneIkkeIverksetteSøknadsbehandling.FantIkkePerson.left()

        inOrder(personServiceMock) {
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
        }
    }

    @Test
    fun `svarer med feil hvis man ikke finner navnet på saksbehandler ved avslag`() {
        val behandling = avslagTilAttestering()

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslag = mock<MicrosoftGraphApiOppslag> {
            on { hentBrukerinformasjonForNavIdent(behandling.saksbehandler) } doReturn MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
        }

        val response = createService(
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslag
        ).opprettJournalpostForAvslag(behandling, attestant)

        response shouldBe Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeJournalføre.left()

        inOrder(personServiceMock, microsoftGraphApiOppslag) {
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(microsoftGraphApiOppslag).hentBrukerinformasjonForNavIdent(argThat { it shouldBe behandling.saksbehandler })
        }
    }

    @Test
    fun `svarer med feil hvis man ikke finner navnet på attestant ved avslag`() {
        val behandling = avslagTilAttestering()

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslag = mock<MicrosoftGraphApiOppslag> {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn graphApiResponse.right() doReturn MicrosoftGraphApiOppslagFeil.DeserialiseringAvResponsFeilet.left()
        }

        val response = createService(
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslag
        ).opprettJournalpostForAvslag(behandling, attestant)

        response shouldBe Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeJournalføre.left()

        inOrder(personServiceMock, microsoftGraphApiOppslag) {
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(microsoftGraphApiOppslag, times(2)).hentBrukerinformasjonForNavIdent(any())
        }
    }

    @Test
    fun `journalfører brev og returnerer journalpost for avlsag`() {
        val behandling = avslagTilAttestering()

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslag = mock<MicrosoftGraphApiOppslag> {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn graphApiResponse.right()
        }

        val brevserviceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn iverksattJournalpostId.right()
        }

        val response = createService(
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslag,
            brevService = brevserviceMock
        ).opprettJournalpostForAvslag(behandling, attestant)

        response shouldBe iverksattJournalpostId.right()

        inOrder(personServiceMock, microsoftGraphApiOppslag, brevserviceMock) {
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(microsoftGraphApiOppslag, times(2)).hentBrukerinformasjonForNavIdent(any())
            verify(brevserviceMock).journalførBrev(
                argThat {
                    it shouldBe AvslagBrevRequest(
                        person = person,
                        avslag = Avslag(
                            opprettet = Tidspunkt.now(fixedClock),
                            avslagsgrunner = emptyList(),
                            harEktefelle = false,
                            beregning = behandling.beregning
                        ),
                        saksbehandlerNavn = graphApiResponse.displayName,
                        attestantNavn = graphApiResponse.displayName
                    )
                },
                argThat { it shouldBe behandling.saksnummer }
            )
        }
    }

    @Test
    fun `svarer med feil hvis journalføring feiler ved avslag`() {
        val behandling = avslagTilAttestering()

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslag = mock<MicrosoftGraphApiOppslag> {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn graphApiResponse.right()
        }

        val brevserviceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn KunneIkkeJournalføreBrev.KunneIkkeGenereBrev.left()
        }

        val response = createService(
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslag,
            brevService = brevserviceMock
        ).opprettJournalpostForAvslag(behandling, attestant)

        response shouldBe Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeJournalføre.left()

        inOrder(personServiceMock, microsoftGraphApiOppslag, brevserviceMock) {
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(microsoftGraphApiOppslag, times(2)).hentBrukerinformasjonForNavIdent(any())
            verify(brevserviceMock).journalførBrev(any(), any())
        }
    }

    @Test
    fun `betaler ut penger og returnerer utbetalings id hvis alt er ok for innvilgelse`() {
        val behandling = innvilgetTilAttestering()

        val utbetalingService = mock<UtbetalingService> {
            on { utbetal(any(), any(), any(), any()) } doReturn utbetaling.right()
        }

        val response = createService(
            utbetalingService = utbetalingService,
        ).iverksettInnvilgning(behandling, attestant)

        response shouldBe utbetaling.id.right()

        verify(utbetalingService).utbetal(
            argThat { it shouldBe behandling.sakId },
            argThat { it shouldBe attestant },
            argThat { it shouldBe behandling.beregning },
            argThat { it shouldBe behandling.simulering }
        )
    }

    @Test
    fun `svarer med feil dersom vi ikke får oversendt utbetaling til oppdrag`() {
        val behandling = innvilgetTilAttestering()

        val utbetalingService = mock<UtbetalingService> {
            on {
                utbetal(
                    any(),
                    any(),
                    any(),
                    any()
                )
            } doReturn KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
        }

        val response = createService(
            utbetalingService = utbetalingService,
        ).iverksettInnvilgning(behandling, attestant)

        response shouldBe Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
    }

    @Test
    fun `svarer med feil ved teknisk feil ved utbretaling`() {
        val behandling = innvilgetTilAttestering()

        val utbetalingService = mock<UtbetalingService> {
            on { utbetal(any(), any(), any(), any()) } doReturn KunneIkkeUtbetale.Protokollfeil.left()
        }

        val response = createService(
            utbetalingService = utbetalingService,
        ).iverksettInnvilgning(behandling, attestant)

        response shouldBe Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.TekniskFeil.left()
    }

    @Test
    fun `svarer med feil dersom vi ikke får simulert`() {
        val behandling = innvilgetTilAttestering()

        val utbetalingService = mock<UtbetalingService> {
            on { utbetal(any(), any(), any(), any()) } doReturn KunneIkkeUtbetale.KunneIkkeSimulere.left()
        }

        val response = createService(
            utbetalingService = utbetalingService,
        ).iverksettInnvilgning(behandling, attestant)

        response shouldBe Statusovergang.KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale.KunneIkkeKontrollsimulere.left()
    }

    private fun innvilgetTilAttestering() =
        Søknadsbehandling.TilAttestering.Innvilget(
            id = behandlingId,
            opprettet = Tidspunkt.now(),
            søknad = Søknad.Journalført.MedOppgave(
                id = BehandlingTestUtils.søknadId,
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                oppgaveId = BehandlingTestUtils.søknadOppgaveId,
                journalpostId = BehandlingTestUtils.søknadJournalpostId
            ),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            saksbehandler = saksbehandler,
            oppgaveId = søknadOppgaveId,
            beregning = beregning,
            simulering = simulering,
        )

    private fun avslagTilAttestering() =
        Søknadsbehandling.TilAttestering.Avslag.MedBeregning(
            id = behandlingId,
            opprettet = Tidspunkt.now(),
            søknad = Søknad.Journalført.MedOppgave(
                id = BehandlingTestUtils.søknadId,
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                oppgaveId = BehandlingTestUtils.søknadOppgaveId,
                journalpostId = BehandlingTestUtils.søknadJournalpostId
            ),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            saksbehandler = saksbehandler,
            oppgaveId = søknadOppgaveId,
            beregning = beregning,
        )

    private val beregning = TestBeregning

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "NAVN",
        datoBeregnet = idag(fixedClock),
        nettoBeløp = 191500,
        periodeList = listOf()
    )

    private val utbetaling = Utbetaling.OversendtUtbetaling.UtenKvittering(
        id = UUID30.randomUUID(),
        opprettet = Tidspunkt.now(fixedClock),
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = emptyList(),
        type = Utbetaling.UtbetalingsType.NY,
        behandler = attestant,
        avstemmingsnøkkel = Avstemmingsnøkkel(),
        simulering = simulering,
        utbetalingsrequest = Utbetalingsrequest(value = "")
    )

    private fun createService(
        behandlingRepo: BehandlingRepo = mock(),
        utbetalingService: UtbetalingService = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        opprettVedtakssnapshotService: OpprettVedtakssnapshotService = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
        clock: Clock = fixedClock,
        microsoftGraphApiClient: MicrosoftGraphApiOppslag = mock(),
        journalførIverksettingService: JournalførIverksettingService = mock(),
        distribuerIverksettingsbrevService: DistribuerIverksettingsbrevService = mock(),
        saksbehandlingRepo: SaksbehandlingRepo = mock(),
        brevService: BrevService = mock(),
    ) = IverksettSaksbehandlingService(
        behandlingRepo,
        utbetalingService,
        oppgaveService,
        personService,
        opprettVedtakssnapshotService,
        behandlingMetrics,
        clock,
        microsoftGraphApiClient,
        journalførIverksettingService,
        distribuerIverksettingsbrevService,
        saksbehandlingRepo,
        brevService
    )
}
