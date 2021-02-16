package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.client.person.MicrosoftGraphResponse
import no.nav.su.se.bakover.common.Tidspunkt
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
import no.nav.su.se.bakover.domain.behandling.avslag.Avslag
import no.nav.su.se.bakover.domain.behandling.avslag.AvslagBrevRequest
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknadsbehandling.Statusovergang
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import org.junit.jupiter.api.Test
import java.time.Clock
import java.util.UUID

internal class IverksettAvslåttSøknadsbehandlingServiceTest {
    private val fnr = FnrGenerator.random()
    private val sakId: UUID = UUID.fromString("268e62fb-3079-4e8d-ab32-ff9fb9eac2ec")
    private val behandlingId: UUID = UUID.fromString("a602aa68-c989-43e3-9fb7-cb488a2a3821")
    private val saksnummer = Saksnummer(999999)
    private val iverksattJournalpostId = JournalpostId("j")
    private val søknadOppgaveId = OppgaveId("søknadOppgaveId")
    private val attestant = NavIdentBruker.Attestant("attestant")
    private val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlinger")
    private val person = Person(
        ident = Ident(
            fnr = BehandlingTestUtils.fnr,
            aktørId = AktørId(aktørId = "123")
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy")
    )
    private val graphApiResponse = MicrosoftGraphResponse(
        displayName = "Nav Navesen",
        givenName = "",
        mail = "",
        officeLocation = "",
        surname = "",
        userPrincipalName = "",
        id = "",
        jobTitle = ""
    )
    private val journalpostId = JournalpostId("jpid")
    private val brevbestillingId = BrevbestillingId("bid")

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
    fun `forsøker å distribuere brev og lukke oppgave ved avslag og returnerer innsendt søknadsbehandling ved feil`() {
        val behandling = iverksattAvslag()

        val brevServiceMock = mock<BrevService>() {
            on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
        }

        val oppgaveServiceMock = mock<OppgaveService>() {
            on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
        }

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val response = createService(
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            behandlingMetrics = behandlingMetricsMock
        ).distribuerBrevOgLukkOppgaveForAvslag(behandling)

        inOrder(brevServiceMock, oppgaveServiceMock) {
            verify(brevServiceMock).distribuerBrev(journalpostId)
            verify(oppgaveServiceMock).lukkOppgave(søknadOppgaveId)
            verifyZeroInteractions(behandlingMetricsMock)
        }

        response shouldBe behandling
    }

    @Test
    fun `returnerer med oppdatert brevbestillingsid dersom brevdistribusjon går bra`() {
        val behandling = iverksattAvslag()

        val brevServiceMock = mock<BrevService>() {
            on { distribuerBrev(any()) } doReturn brevbestillingId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService>() {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val behandlingMetricsMock = mock<BehandlingMetrics>()

        val response = createService(
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,
            behandlingMetrics = behandlingMetricsMock
        ).distribuerBrevOgLukkOppgaveForAvslag(behandling)

        inOrder(brevServiceMock, oppgaveServiceMock, behandlingMetricsMock) {
            verify(brevServiceMock).distribuerBrev(journalpostId)
            verify(behandlingMetricsMock).incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.DISTRIBUERT_BREV)
            verify(oppgaveServiceMock).lukkOppgave(søknadOppgaveId)
            verify(behandlingMetricsMock).incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.LUKKET_OPPGAVE)
        }

        response shouldBe behandling.copy(
            eksterneIverksettingsteg = Søknadsbehandling.Iverksatt.Avslag.EksterneIverksettingsteg.JournalførtOgDistribuertBrev(
                journalpostId, brevbestillingId
            )
        )
    }

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

    private fun iverksattAvslag() = avslagTilAttestering()
        .tilIverksatt(
            Attestering.Iverksatt(attestant),
            Søknadsbehandling.Iverksatt.Avslag.EksterneIverksettingsteg.Journalført(journalpostId)
        )

    private fun createService(
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
        clock: Clock = fixedClock,
        microsoftGraphApiClient: MicrosoftGraphApiOppslag = mock(),
        brevService: BrevService = mock(),
    ) = IverksettAvslåttSøknadsbehandlingService(
        oppgaveService = oppgaveService,
        personService = personService,
        behandlingMetrics = behandlingMetrics,
        clock = clock,
        microsoftGraphApiClient = microsoftGraphApiClient,
        brevService = brevService
    )
}
