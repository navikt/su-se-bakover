package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doReturnConsecutively
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.attestant
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.behandlingsinformasjon
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.fnr
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.person
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.saksbehandler
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.saksnummer
import no.nav.su.se.bakover.service.behandling.DistribuerIverksettingsbrevService
import no.nav.su.se.bakover.service.behandling.JournalførIverksettingService
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class FerdigstillSøknadsbehandingIverksettingServiceTest {

    private val iverksattOppgaveId = OppgaveId("iverksattOppgaveId")

    private val iverksattJournalpostId = JournalpostId("iverksattJournalpostId")

    private val iverksattBrevbestillingId = BrevbestillingId("iverattBrevbestillingId")

    private val innvilgetBehandlingUtenJournalpost = Søknadsbehandling.Iverksatt.Innvilget(
        id = UUID.randomUUID(),
        sakId = UUID.randomUUID(),
        opprettet = Tidspunkt.EPOCH,
        saksbehandler = saksbehandler,
        attestering = Attestering.Iverksatt(attestant),
        oppgaveId = iverksattOppgaveId,
        saksnummer = saksnummer,
        søknad = Søknad.Journalført.MedOppgave(
            id = BehandlingTestUtils.søknadId,
            opprettet = Tidspunkt.EPOCH,
            sakId = BehandlingTestUtils.sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = BehandlingTestUtils.søknadOppgaveId,
            journalpostId = BehandlingTestUtils.søknadJournalpostId
        ),
        beregning = TestBeregning,
        simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "",
            datoBeregnet = LocalDate.EPOCH,
            nettoBeløp = 0,
            periodeList = listOf()
        ),
        behandlingsinformasjon = behandlingsinformasjon,
        fnr = fnr,
        utbetalingId = UUID30.randomUUID(),
        eksterneIverksettingsteg = Søknadsbehandling.Iverksatt.Innvilget.EksterneIverksettingsteg.VenterPåKvittering
    )

    @Test
    fun `Kunne ikke opprette journalpost hvis vi ikke finner saksbehandler`() {
        val behandlingRepoMock = mock<SøknadsbehandlingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) }.doReturn(
                MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left(),
                Either.right(BehandlingTestUtils.microsoftGraphMock.response)
            )
        }

        val journalførIverksettingServiceMock = mock<JournalførIverksettingService>()

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService>()

        val actual = createService(
            søknadsbehandlingRepo = behandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,

        ).ferdigstillInnvilgelse(innvilgetBehandlingUtenJournalpost)

        actual shouldBe Unit

        inOrder(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            personServiceMock,
            oppslagMock,
            journalførIverksettingServiceMock,
            oppgaveServiceMock
        ) {
            verify(personServiceMock).hentPersonMedSystembruker(fnr)
            verify(oppslagMock).hentBrukerinformasjonForNavIdent(
                argThat {
                    it shouldBe saksbehandler
                }
            )
            verify(oppgaveServiceMock).lukkOppgaveMedSystembruker(argThat { it shouldBe iverksattOppgaveId })
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            personServiceMock,
            oppslagMock,
            journalførIverksettingServiceMock,
            oppgaveServiceMock
        )
    }

    @Test
    fun `Kunne ikke opprette journalpost hvis vi ikke finner attestant`() {
        val behandlingRepoMock = mock<SøknadsbehandlingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturnConsecutively listOf(
                BehandlingTestUtils.microsoftGraphMock.response.right(),
                MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
            )
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val actual = createService(
            søknadsbehandlingRepo = behandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,

        ).ferdigstillInnvilgelse(innvilgetBehandlingUtenJournalpost)

        actual shouldBe Unit

        inOrder(
            behandlingRepoMock,
            personServiceMock,
            oppslagMock,
            oppgaveServiceMock
        ) {
            verify(personServiceMock).hentPersonMedSystembruker(fnr)
            argumentCaptor<NavIdentBruker>().apply {
                verify(oppslagMock, times(2)).hentBrukerinformasjonForNavIdent(capture())
                firstValue shouldBe saksbehandler
                secondValue shouldBe attestant
            }
            verify(oppgaveServiceMock).lukkOppgaveMedSystembruker(argThat { it shouldBe iverksattOppgaveId })
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            personServiceMock,
            oppslagMock,
            oppgaveServiceMock
        )
    }

    @Test
    fun `Kunne ikke opprette journalpost hvis vi ikke finner person`() {
        val behandlingRepoMock = mock<SøknadsbehandlingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn BehandlingTestUtils.microsoftGraphMock.response.right()
        }

        val brevServiceMock = mock<BrevService>()

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val actual = createService(
            søknadsbehandlingRepo = behandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            brevService = brevServiceMock

        ).ferdigstillInnvilgelse(innvilgetBehandlingUtenJournalpost)

        actual shouldBe Unit

        inOrder(
            personServiceMock,
            oppgaveServiceMock,
            oppslagMock
        ) {
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).lukkOppgaveMedSystembruker(argThat { it shouldBe iverksattOppgaveId })
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            brevServiceMock,
            personServiceMock,
            oppslagMock,
            oppgaveServiceMock
        )
    }

    @Test
    fun `Kan ikke journalføre eller distribuere brev hvis journalføring feiler`() {

        val saksbehandlingRepoMock = mock<SøknadsbehandlingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturnConsecutively listOf(
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = saksbehandler.navIdent).right(),
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = attestant.navIdent).right(),
            )
        }
        val brevServiceMock = mock<BrevService>() {
            on { journalførBrev(any(), any()) } doReturn KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost.left()
        }
        val oppgaveServiceMock = mock<OppgaveService>() {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val actual = createService(
            søknadsbehandlingRepo = saksbehandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            brevService = brevServiceMock,
            oppgaveService = oppgaveServiceMock,

        ).ferdigstillInnvilgelse(innvilgetBehandlingUtenJournalpost)

        actual shouldBe Unit

        inOrder(
            saksbehandlingRepoMock,
            personServiceMock,
            oppslagMock,
            brevServiceMock,
            oppgaveServiceMock
        ) {
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            argumentCaptor<NavIdentBruker>().apply {
                verify(oppslagMock, times(2)).hentBrukerinformasjonForNavIdent(capture())
                firstValue shouldBe saksbehandler
                secondValue shouldBe attestant
            }
            verify(brevServiceMock).journalførBrev(
                argThat {
                    it shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        beregning = innvilgetBehandlingUtenJournalpost.beregning,
                        behandlingsinformasjon = innvilgetBehandlingUtenJournalpost.behandlingsinformasjon,
                        saksbehandlerNavn = saksbehandler.navIdent,
                        attestantNavn = attestant.navIdent,
                    )
                },
                argThat { it shouldBe saksnummer }
            )
            verify(oppgaveServiceMock).lukkOppgaveMedSystembruker(argThat { it shouldBe iverksattOppgaveId })
        }
        verifyNoMoreInteractions(
            saksbehandlingRepoMock,
            personServiceMock,
            oppslagMock,
            oppgaveServiceMock
        )
    }

    @Test
    fun `Kunne ikke distribuere brev`() {
        val saksbehandlingRepoMock = mock<SøknadsbehandlingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturnConsecutively listOf(
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = saksbehandler.navIdent).right(),
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = attestant.navIdent).right(),
            )
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn iverksattJournalpostId.right()
            on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val actual = createService(
            søknadsbehandlingRepo = saksbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            brevService = brevServiceMock,
        ).ferdigstillInnvilgelse(innvilgetBehandlingUtenJournalpost)

        actual shouldBe Unit

        inOrder(
            saksbehandlingRepoMock,
            personServiceMock,
            oppslagMock,
            brevServiceMock,
            oppgaveServiceMock
        ) {
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            argumentCaptor<NavIdentBruker>().apply {
                verify(oppslagMock, times(2)).hentBrukerinformasjonForNavIdent(capture())
                firstValue shouldBe saksbehandler
                secondValue shouldBe attestant
            }
            verify(brevServiceMock).journalførBrev(
                argThat {
                    it shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        beregning = innvilgetBehandlingUtenJournalpost.beregning,
                        behandlingsinformasjon = innvilgetBehandlingUtenJournalpost.behandlingsinformasjon,
                        saksbehandlerNavn = saksbehandler.navIdent,
                        attestantNavn = attestant.navIdent,
                    )
                },
                argThat { it shouldBe saksnummer },
            )
            verify(saksbehandlingRepoMock).lagre(
                argThat {
                    it shouldBe innvilgetBehandlingUtenJournalpost.copy(
                        eksterneIverksettingsteg = Søknadsbehandling.Iverksatt.Innvilget.EksterneIverksettingsteg.Journalført(
                            iverksattJournalpostId
                        )
                    )
                }
            )
            verify(brevServiceMock).distribuerBrev(
                argThat {
                    it shouldBe iverksattJournalpostId
                }
            )
            verify(oppgaveServiceMock).lukkOppgaveMedSystembruker(argThat { it shouldBe iverksattOppgaveId })
        }
        verifyNoMoreInteractions(
            saksbehandlingRepoMock,
            personServiceMock,
            oppslagMock,
            brevServiceMock,
            oppgaveServiceMock
        )
    }

    @Test
    fun `journalfører og distribuerer brev for iverksatt innvilget`() {
        val saksbehandlingRepoMock = mock<SøknadsbehandlingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn iverksattJournalpostId.right()
            on { distribuerBrev(any()) } doReturn iverksattBrevbestillingId.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturnConsecutively listOf(
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = saksbehandler.navIdent).right(),
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = attestant.navIdent).right(),
            )
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val actual = createService(
            søknadsbehandlingRepo = saksbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            brevService = brevServiceMock,
        ).ferdigstillInnvilgelse(innvilgetBehandlingUtenJournalpost)

        actual shouldBe Unit

        verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
        argumentCaptor<NavIdentBruker>().apply {
            verify(oppslagMock, times(2)).hentBrukerinformasjonForNavIdent(capture())
            firstValue shouldBe saksbehandler
            secondValue shouldBe attestant
        }
        verify(brevServiceMock).journalførBrev(
            argThat {
                it shouldBe LagBrevRequest.InnvilgetVedtak(
                    person = person,
                    beregning = innvilgetBehandlingUtenJournalpost.beregning,
                    behandlingsinformasjon = innvilgetBehandlingUtenJournalpost.behandlingsinformasjon,
                    saksbehandlerNavn = saksbehandler.navIdent,
                    attestantNavn = attestant.navIdent,
                )
            },
            argThat { it shouldBe saksnummer },
        )
        argumentCaptor<Søknadsbehandling>().apply {
            verify(saksbehandlingRepoMock, times(2)).lagre(capture())
            firstValue shouldBe innvilgetBehandlingUtenJournalpost.copy(
                eksterneIverksettingsteg = Søknadsbehandling.Iverksatt.Innvilget.EksterneIverksettingsteg.Journalført(
                    iverksattJournalpostId
                )
            )
            secondValue shouldBe innvilgetBehandlingUtenJournalpost.copy(
                eksterneIverksettingsteg = Søknadsbehandling.Iverksatt.Innvilget.EksterneIverksettingsteg.JournalførtOgDistribuertBrev(
                    iverksattJournalpostId,
                    iverksattBrevbestillingId
                )
            )
        }
        verify(brevServiceMock).distribuerBrev(
            argThat {
                it shouldBe iverksattJournalpostId
            }
        )
        verify(oppgaveServiceMock).lukkOppgaveMedSystembruker(argThat { it shouldBe iverksattOppgaveId })

        verifyNoMoreInteractions(
            saksbehandlingRepoMock,
            brevServiceMock,
            personServiceMock,
            oppslagMock,
            oppgaveServiceMock
        )
    }

    private fun createService(
        søknadsbehandlingRepo: SøknadsbehandlingRepo = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
        microsoftGraphApiOppslag: MicrosoftGraphApiOppslag = mock(),
        brevService: BrevService = mock(),
    ) = FerdigstillSøknadsbehandingIverksettingServiceImpl(
        søknadsbehandlingRepo = søknadsbehandlingRepo,
        oppgaveService = oppgaveService,
        behandlingMetrics = behandlingMetrics,
        microsoftGraphApiClient = microsoftGraphApiOppslag,
        personService = personService,
        brevService = brevService,
    )
}
