package no.nav.su.se.bakover.service.behandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
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
import no.nav.su.se.bakover.database.SaksbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.attestant
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.behandlingsinformasjon
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.fnr
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.person
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.saksbehandler
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.saksnummer
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeDistribuereBrev
import no.nav.su.se.bakover.service.brev.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

internal class FerdigstillIverksettingServiceTest {

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
        val behandlingRepoMock = mock<SaksbehandlingRepo>()

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
            saksbehandlingRepo = behandlingRepoMock,
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
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
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
        val behandlingRepoMock = mock<SaksbehandlingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) }.doReturn(
                Either.right(BehandlingTestUtils.microsoftGraphMock.response),
                MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
            )
        }

        val journalførIverksettingServiceMock = mock<JournalførIverksettingService>()

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService>()

        val actual = createService(
            saksbehandlingRepo = behandlingRepoMock,
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
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe fnr })
            argumentCaptor<NavIdentBruker>().apply {
                verify(oppslagMock, times(2)).hentBrukerinformasjonForNavIdent(capture())
                firstValue shouldBe saksbehandler
                secondValue shouldBe attestant
            }
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
    fun `Kunne ikke opprette journalpost hvis vi ikke finner person`() {
        val behandlingRepoMock = mock<SaksbehandlingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn Either.right(BehandlingTestUtils.microsoftGraphMock.response)
        }

        val brevServiceMock = mock<BrevService>()

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val actual = createService(
            saksbehandlingRepo = behandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            brevService = brevServiceMock

            ).ferdigstillInnvilgelse(innvilgetBehandlingUtenJournalpost)

        actual shouldBe Unit

        inOrder(
            personServiceMock,
            oppgaveServiceMock
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

        val saksbehandlingRepoMock = mock<SaksbehandlingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn Either.right(BehandlingTestUtils.microsoftGraphMock.response)
        }
        val brevServiceMock = mock<BrevService>() {
            on { journalførBrev(any(), any()) } doReturn KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost.left()
        }
        val oppgaveServiceMock = mock<OppgaveService>() {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val actual = createService(
            saksbehandlingRepo = saksbehandlingRepoMock,
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
                        saksbehandlerNavn = BehandlingTestUtils.microsoftGraphMock.response.displayName,
                        attestantNavn = BehandlingTestUtils.microsoftGraphMock.response.displayName,
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
        val saksbehandlingRepoMock = mock<SaksbehandlingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn Either.right(BehandlingTestUtils.microsoftGraphMock.response)
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn iverksattJournalpostId.right()
            on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val actual = createService(
            saksbehandlingRepo = saksbehandlingRepoMock,
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
                        saksbehandlerNavn = BehandlingTestUtils.microsoftGraphMock.response.displayName,
                        attestantNavn = BehandlingTestUtils.microsoftGraphMock.response.displayName,
                    )
                },
                argThat { it shouldBe saksnummer },
            )
            verify(saksbehandlingRepoMock).lagre(argThat {
                it shouldBe innvilgetBehandlingUtenJournalpost.copy(
                    eksterneIverksettingsteg = Søknadsbehandling.Iverksatt.Innvilget.EksterneIverksettingsteg.Journalført(iverksattJournalpostId)
                )
            })
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
        val saksbehandlingRepoMock = mock<SaksbehandlingRepo>()

        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn iverksattJournalpostId.right()
            on { distribuerBrev(any()) } doReturn iverksattBrevbestillingId.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn Either.right(BehandlingTestUtils.microsoftGraphMock.response)
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
        }

        val actual = createService(
            saksbehandlingRepo = saksbehandlingRepoMock,
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
                        saksbehandlerNavn = BehandlingTestUtils.microsoftGraphMock.response.displayName,
                        attestantNavn = BehandlingTestUtils.microsoftGraphMock.response.displayName,
                    )
                },
                argThat { it shouldBe saksnummer },
            )
            argumentCaptor<Søknadsbehandling>().apply {
                verify(saksbehandlingRepoMock, times(2)).lagre(capture())
                firstValue shouldBe innvilgetBehandlingUtenJournalpost.copy(
                    eksterneIverksettingsteg = Søknadsbehandling.Iverksatt.Innvilget.EksterneIverksettingsteg.Journalført(iverksattJournalpostId)
                )
                secondValue shouldBe innvilgetBehandlingUtenJournalpost.copy(
                    eksterneIverksettingsteg = Søknadsbehandling.Iverksatt.Innvilget.EksterneIverksettingsteg.JournalførtOgDistribuertBrev(iverksattJournalpostId,iverksattBrevbestillingId)
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
        saksbehandlingRepo: SaksbehandlingRepo = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
        microsoftGraphApiOppslag: MicrosoftGraphApiOppslag = mock(),
        brevService: BrevService = mock(),
    ) = FerdigstillIverksettingServiceImpl(
        saksbehandlingRepo = saksbehandlingRepo,
        oppgaveService = oppgaveService,
        behandlingMetrics = behandlingMetrics,
        microsoftGraphApiClient = microsoftGraphApiOppslag,
        personService = personService,
        brevService = brevService,
    )
}
