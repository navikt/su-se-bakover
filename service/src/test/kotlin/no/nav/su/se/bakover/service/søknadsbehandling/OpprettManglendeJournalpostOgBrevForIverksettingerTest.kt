package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doReturnConsecutively
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.UUID30
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
import no.nav.su.se.bakover.domain.behandling.withVilkårAvslått
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegEtterUtbetaling
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.EksterneIverksettingsstegForAvslag
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class OpprettManglendeJournalpostOgBrevForIverksettingerTest {
    private val fnr = FnrGenerator.random()
    private val person = Person(
        ident = Ident(
            fnr = fnr,
            aktørId = AktørId(aktørId = "123")
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy")
    )
    private val sakIdJournalpost = UUID.randomUUID()
    private val sakIdBestiltBrev = UUID.randomUUID()

    private val behandlingIdJournalpost = UUID.randomUUID()
    private val behandlingIdBestiltBrev = UUID.randomUUID()

    private val journalpostId = JournalpostId("1")
    private val journalpostIdBestiltBrev = JournalpostId("1")

    private val brevbestillingId = BrevbestillingId("2")

    private val journalpostOgBrevdistribusjonResultat =
        FerdigstillIverksettingService.OpprettetJournalpostForIverksetting(
            sakIdJournalpost,
            behandlingIdJournalpost,
            journalpostId
        )
    private val bestiltBrev =
        FerdigstillIverksettingService.BestiltBrev(
            sakIdBestiltBrev,
            behandlingIdBestiltBrev,
            journalpostIdBestiltBrev,
            brevbestillingId
        )

    private val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler")
    private val attestant = NavIdentBruker.Attestant("attestant")
    private val saksbehandlerNavn = "Saksbehandler Saksbehandlson"
    private val attestantNavn = "Attestant Attestantsen"

    private val innvilgetBehandlingUtenJournalpost = Søknadsbehandling.Iverksatt.Innvilget(
        søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakIdJournalpost,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = OppgaveId("1"),
            journalpostId = journalpostId
        ),
        id = behandlingIdJournalpost,
        saksbehandler = saksbehandler,
        attestering = Attestering.Iverksatt(attestant),
        sakId = sakIdJournalpost,
        saksnummer = Saksnummer(1),
        fnr = fnr,
        oppgaveId = OppgaveId("1"),
        opprettet = Tidspunkt.EPOCH,
        beregning = TestBeregning,
        simulering = Simulering(
            gjelderId = BehandlingTestUtils.fnr,
            gjelderNavn = "",
            datoBeregnet = LocalDate.EPOCH,
            nettoBeløp = 0,
            periodeList = listOf()
        ),
        behandlingsinformasjon = BehandlingTestUtils.behandlingsinformasjon,
        utbetalingId = UUID30.randomUUID()
    )

    private val avslagUtenBrevbestilling = Søknadsbehandling.Iverksatt.Avslag.UtenBeregning(
        søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakIdJournalpost,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = OppgaveId("1"),
            journalpostId = journalpostId
        ),
        id = behandlingIdJournalpost,
        saksbehandler = saksbehandler,
        attestering = Attestering.Iverksatt(attestant),
        sakId = sakIdJournalpost,
        saksnummer = Saksnummer(1),
        fnr = fnr,
        oppgaveId = OppgaveId("1"),
        opprettet = Tidspunkt.EPOCH,
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withVilkårAvslått(),
        eksterneIverksettingsteg = EksterneIverksettingsstegForAvslag.Journalført(journalpostId),
    )

    @Test
    fun `Gjør ingenting hvis det ikke er noe å gjøre`() {
        val behandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hentIverksatteBehandlingerUtenJournalposteringer() } doReturn emptyList()
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn emptyList()
        }

        val actual = createService(
            søknadsbehandlingRepo = behandlingRepoMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe FerdigstillIverksettingService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = emptyList(),
            brevbestillingsresultat = emptyList()
        )

        inOrder(
            behandlingRepoMock
        ) {
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenJournalposteringer()

            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenBrevbestillinger()
        }
        verifyNoMoreInteractions(behandlingRepoMock)
    }

    @Test
    fun `Kunne ikke opprette journalpost hvis vi ikke finner saksbehandler`() {
        val behandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hentIverksatteBehandlingerUtenJournalposteringer() } doReturn listOf(innvilgetBehandlingUtenJournalpost)
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn emptyList()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn FantIkkeBrukerForNavIdent.left()
        }

        val actual = createService(
            søknadsbehandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe FerdigstillIverksettingService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = listOf(
                FerdigstillIverksettingService.KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = sakIdJournalpost,
                    behandlingId = behandlingIdJournalpost,
                    grunn = "FikkIkkeHentetSaksbehandlerEllerAttestant"
                ).left()
            ),
            brevbestillingsresultat = emptyList()
        )

        inOrder(
            behandlingRepoMock,
            personServiceMock,
            oppslagMock
        ) {
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenJournalposteringer()
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            verify(oppslagMock).hentBrukerinformasjonForNavIdent(argThat { it shouldBe saksbehandler })

            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenBrevbestillinger()
        }
        verifyNoMoreInteractions(behandlingRepoMock, personServiceMock, oppslagMock)
    }

    @Test
    fun `Kunne ikke opprette journalpost hvis vi ikke finner attestant`() {
        val behandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hentIverksatteBehandlingerUtenJournalposteringer() } doReturn listOf(innvilgetBehandlingUtenJournalpost)
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn emptyList()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) }.doReturn(
                BehandlingTestUtils.microsoftGraphMock.response.right(),
                FantIkkeBrukerForNavIdent.left()
            )
        }

        val actual = createService(
            søknadsbehandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe FerdigstillIverksettingService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = listOf(
                FerdigstillIverksettingService.KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = sakIdJournalpost,
                    behandlingId = behandlingIdJournalpost,
                    grunn = "FikkIkkeHentetSaksbehandlerEllerAttestant"
                ).left()
            ),
            brevbestillingsresultat = emptyList()
        )

        inOrder(
            behandlingRepoMock,
            personServiceMock,
            oppslagMock
        ) {
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenJournalposteringer()
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            argumentCaptor<NavIdentBruker>().apply {
                verify(oppslagMock, times(2)).hentBrukerinformasjonForNavIdent(capture())
                firstValue shouldBe saksbehandler
                secondValue shouldBe attestant
            }
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenBrevbestillinger()
        }
        verifyNoMoreInteractions(behandlingRepoMock, personServiceMock, oppslagMock)
    }

    @Test
    fun `Kunne ikke opprette journalpost hvis vi ikke finner person`() {
        val behandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hentIverksatteBehandlingerUtenJournalposteringer() } doReturn listOf(innvilgetBehandlingUtenJournalpost)
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn emptyList()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturnConsecutively listOf(
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = saksbehandlerNavn).right(),
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = attestantNavn).right(),
            )
        }

        val actual = createService(
            søknadsbehandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe FerdigstillIverksettingService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = listOf(
                FerdigstillIverksettingService.KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = sakIdJournalpost,
                    behandlingId = behandlingIdJournalpost,
                    grunn = "FantIkkePerson"
                ).left()
            ),
            brevbestillingsresultat = emptyList()
        )

        inOrder(
            behandlingRepoMock,
            personServiceMock,
            oppslagMock
        ) {
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenJournalposteringer()
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenBrevbestillinger()
        }
        verifyNoMoreInteractions(behandlingRepoMock, personServiceMock, oppslagMock)
    }

    @Test
    fun `Kunne ikke journalføre brev når vi ikke klarer å lage det`() {
        val behandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hentIverksatteBehandlingerUtenJournalposteringer() } doReturn listOf(innvilgetBehandlingUtenJournalpost)
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn emptyList()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val brevServiceMock = mock<BrevService> {
            on {
                journalførBrev(any(), any())
            } doReturn KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost.left()
        }

        val oppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturnConsecutively listOf(
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = saksbehandlerNavn).right(),
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = attestantNavn).right(),
            )
        }

        val actual = createService(
            søknadsbehandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            brevService = brevServiceMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe FerdigstillIverksettingService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = listOf(
                FerdigstillIverksettingService.KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = sakIdJournalpost,
                    behandlingId = behandlingIdJournalpost,
                    grunn = "KunneIkkeOppretteJournalpost"
                ).left()
            ),
            brevbestillingsresultat = emptyList()
        )

        inOrder(
            behandlingRepoMock,
            brevServiceMock,
            personServiceMock,
            oppslagMock,
        ) {
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenJournalposteringer()
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
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = attestantNavn,
                    )
                },
                argThat { it shouldBe innvilgetBehandlingUtenJournalpost.saksnummer }
            )

            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenBrevbestillinger()
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            brevServiceMock,
            personServiceMock,
            oppslagMock,
        )
    }

    @Test
    fun `Kan ikke bestille brev hvis iverksattJournalId er null`() {
        val behandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hentIverksatteBehandlingerUtenJournalposteringer() } doReturn emptyList()
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn listOf(
                innvilgetBehandlingUtenJournalpost.copy(
                    id = behandlingIdBestiltBrev,
                    sakId = sakIdBestiltBrev,
                )
            )
        }

        val actual = createService(
            søknadsbehandlingRepo = behandlingRepoMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe FerdigstillIverksettingService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = emptyList(),
            brevbestillingsresultat = listOf(
                FerdigstillIverksettingService.KunneIkkeBestilleBrev(
                    sakId = sakIdBestiltBrev,
                    behandlingId = behandlingIdBestiltBrev,
                    journalpostId = null,
                    grunn = "MåJournalføresFørst"
                ).left()
            )
        )

        inOrder(
            behandlingRepoMock
        ) {
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenJournalposteringer()
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenBrevbestillinger()
        }
        verifyNoMoreInteractions(behandlingRepoMock)
    }

    @Test
    fun `Kunne ikke distribuere brev`() {
        val behandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hentIverksatteBehandlingerUtenJournalposteringer() } doReturn emptyList()
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn listOf(
                innvilgetBehandlingUtenJournalpost.copy(
                    id = behandlingIdBestiltBrev,
                    sakId = sakIdBestiltBrev,
                    eksterneIverksettingsteg = EksterneIverksettingsstegEtterUtbetaling.Journalført(
                        journalpostId
                    )
                )
            )
        }

        val brevServiceMock = mock<BrevService>() {
            on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
        }
        val actual = createService(
            søknadsbehandlingRepo = behandlingRepoMock,
            brevService = brevServiceMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe FerdigstillIverksettingService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = emptyList(),
            brevbestillingsresultat = listOf(
                FerdigstillIverksettingService.KunneIkkeBestilleBrev(
                    sakId = sakIdBestiltBrev,
                    behandlingId = behandlingIdBestiltBrev,
                    journalpostId = journalpostIdBestiltBrev,
                    grunn = "FeilVedDistribueringAvBrev"
                ).left()
            )
        )

        inOrder(
            behandlingRepoMock,
            brevServiceMock,
        ) {
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenJournalposteringer()
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenBrevbestillinger()
            verify(brevServiceMock).distribuerBrev(
                argThat {
                    it shouldBe journalpostId
                }
            )
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            brevServiceMock,
        )
    }

    @Test
    fun `distribuerer brev for iverksatt avslag`() {
        val behandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn listOf(
                avslagUtenBrevbestilling.copy(
                    id = behandlingIdBestiltBrev,
                    sakId = sakIdBestiltBrev
                )
            )
        }

        val brevServiceMock = mock<BrevService> {
            on { distribuerBrev(any()) } doReturn brevbestillingId.right()
        }

        val actual = createService(
            søknadsbehandlingRepo = behandlingRepoMock,
            brevService = brevServiceMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe FerdigstillIverksettingService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = emptyList(),
            brevbestillingsresultat = listOf(bestiltBrev.right())
        )

        inOrder(
            behandlingRepoMock,
            brevServiceMock,
        ) {
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenJournalposteringer()
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenBrevbestillinger()
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe journalpostId })
            verify(behandlingRepoMock).lagre(
                argThat {
                    it shouldBe avslagUtenBrevbestilling.copy(
                        id = behandlingIdBestiltBrev,
                        sakId = sakIdBestiltBrev,
                        eksterneIverksettingsteg = EksterneIverksettingsstegForAvslag.JournalførtOgDistribuertBrev(
                            avslagUtenBrevbestilling.eksterneIverksettingsteg.journalpostId,
                            brevbestillingId
                        )
                    )
                }
            )
        }
        verifyNoMoreInteractions(behandlingRepoMock, brevServiceMock)
    }

    @Test
    fun `journalfører og distribuerer brev for iverksatt innvilget`() {
        val behandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hentIverksatteBehandlingerUtenJournalposteringer() } doReturn listOf(innvilgetBehandlingUtenJournalpost)
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn listOf(
                innvilgetBehandlingUtenJournalpost.copy(
                    id = behandlingIdBestiltBrev,
                    sakId = sakIdBestiltBrev,
                    eksterneIverksettingsteg = EksterneIverksettingsstegEtterUtbetaling.Journalført(
                        journalpostIdBestiltBrev
                    )
                ),
            )
        }

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn journalpostIdBestiltBrev.right()
            on { distribuerBrev(any()) } doReturn brevbestillingId.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturnConsecutively listOf(
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = saksbehandlerNavn).right(),
                BehandlingTestUtils.microsoftGraphMock.response.copy(displayName = attestantNavn).right(),
            )
        }

        val actual = createService(
            søknadsbehandlingRepo = behandlingRepoMock,
            brevService = brevServiceMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe FerdigstillIverksettingService.OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = listOf(journalpostOgBrevdistribusjonResultat.right()),
            brevbestillingsresultat = listOf(bestiltBrev.right())
        )

        inOrder(
            behandlingRepoMock,
            personServiceMock,
            oppslagMock,
            brevServiceMock,
            brevServiceMock,
        ) {
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenJournalposteringer()
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            argumentCaptor<NavIdentBruker>().apply {
                verify(oppslagMock, times(2)).hentBrukerinformasjonForNavIdent(capture())
                firstValue shouldBe saksbehandler
                secondValue shouldBe attestant
            }
            val lagreCaptor = argumentCaptor<Søknadsbehandling>()
            verify(brevServiceMock).journalførBrev(
                argThat {
                    it shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        beregning = innvilgetBehandlingUtenJournalpost.beregning,
                        behandlingsinformasjon = innvilgetBehandlingUtenJournalpost.behandlingsinformasjon,
                        saksbehandlerNavn = saksbehandlerNavn,
                        attestantNavn = attestantNavn,
                    )
                },
                argThat { it shouldBe innvilgetBehandlingUtenJournalpost.saksnummer },
            )
            verify(behandlingRepoMock).lagre(lagreCaptor.capture()).let {
                lagreCaptor.firstValue shouldBe innvilgetBehandlingUtenJournalpost.copy(
                    eksterneIverksettingsteg = EksterneIverksettingsstegEtterUtbetaling.Journalført(
                        journalpostIdBestiltBrev
                    )
                )
            }
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenBrevbestillinger()
            verify(brevServiceMock).distribuerBrev(argThat { it shouldBe journalpostIdBestiltBrev })
            verify(behandlingRepoMock).lagre(lagreCaptor.capture()).let {
                lagreCaptor.secondValue shouldBe innvilgetBehandlingUtenJournalpost.copy(
                    id = behandlingIdBestiltBrev,
                    sakId = sakIdBestiltBrev,
                    eksterneIverksettingsteg = EksterneIverksettingsstegEtterUtbetaling.JournalførtOgDistribuertBrev(
                        journalpostIdBestiltBrev,
                        brevbestillingId
                    )
                )
            }
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            brevServiceMock,
            personServiceMock,
            oppslagMock
        )
    }

    private fun createService(
        søknadsbehandlingRepo: SøknadsbehandlingRepo = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
        microsoftGraphApiOppslag: MicrosoftGraphApiOppslag = mock(),
        brevService: BrevService = mock(),
        clock: Clock = Clock.systemUTC(),
        revurderingRepo: RevurderingRepo = mock()
    ) = FerdigstillIverksettingServiceImpl(
        søknadsbehandlingRepo = søknadsbehandlingRepo,
        oppgaveService = oppgaveService,
        personService = personService,
        behandlingMetrics = behandlingMetrics,
        microsoftGraphApiClient = microsoftGraphApiOppslag,
        brevService = brevService,
        clock = clock,
        revurderingRepo = revurderingRepo
    )
}
