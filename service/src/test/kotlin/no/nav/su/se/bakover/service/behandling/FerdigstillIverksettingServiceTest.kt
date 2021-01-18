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
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.brev.LagBrevRequest
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.attestant
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.createOpprettetBehandling
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.fnr
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.person
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.saksbehandler
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import org.junit.jupiter.api.Test

internal class FerdigstillIverksettingServiceTest {

    private val utbetalingId = UUID30.randomUUID()

    private val iverksattOppgaveId = OppgaveId("iverksattOppgaveId")

    private val iverksattJournalpostId = JournalpostId("iverksattJournalpostId")

    private val iverksattBrevbestillingId = BrevbestillingId("iverattBrevbestillingId")

    private val innvilgetBehandlingUtenJournalpost = createOpprettetBehandling().copy(
        status = Behandling.BehandlingsStatus.IVERKSATT_INNVILGET,
        saksbehandler = saksbehandler,
        attestering = Attestering.Iverksatt(attestant),
        iverksattJournalpostId = null,
        iverksattBrevbestillingId = null,
        oppgaveId = iverksattOppgaveId,
    )

    @Test
    fun `Gjør ingenting hvis det ikke er noe å gjøre`() {
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandlingForUtbetaling(any()) } doReturn null
        }

        val personServiceMock = mock<PersonService>()
        val oppslagMock = mock<MicrosoftGraphApiOppslag>()
        val journalførIverksettingServiceMock = mock<JournalførIverksettingService>()
        val oppgaveServiceMock = mock<OppgaveService>()
        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService>()

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            journalførIverksettingService = journalførIverksettingServiceMock,
            oppgaveService = oppgaveServiceMock,

        ).ferdigstillInnvilgelse(utbetalingId)

        actual shouldBe Unit

        inOrder(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            personServiceMock,
            oppslagMock,
            journalførIverksettingServiceMock,
            oppgaveServiceMock
        ) {
            verify(behandlingRepoMock).hentBehandlingForUtbetaling(argThat { it shouldBe utbetalingId })
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
    fun `Kunne ikke opprette journalpost hvis vi ikke finner saksbehandler`() {
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandlingForUtbetaling(any()) } doReturn innvilgetBehandlingUtenJournalpost
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) }.doReturn(
                MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left(),
                Either.right(BehandlingTestUtils.microsoftGraphMock.response)
            )
        }

        val journalførIverksettingServiceMock = mock<JournalførIverksettingService>()

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService>()

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            journalførIverksettingService = journalførIverksettingServiceMock,
            oppgaveService = oppgaveServiceMock,

        ).ferdigstillInnvilgelse(utbetalingId)

        actual shouldBe Unit

        inOrder(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            personServiceMock,
            oppslagMock,
            journalførIverksettingServiceMock,
            oppgaveServiceMock
        ) {
            verify(behandlingRepoMock).hentBehandlingForUtbetaling(argThat { it shouldBe utbetalingId })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(oppslagMock).hentBrukerinformasjonForNavIdent(
                argThat {
                    it shouldBe saksbehandler
                }
            )
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe iverksattOppgaveId })
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
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandlingForUtbetaling(any()) } doReturn innvilgetBehandlingUtenJournalpost
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) }.doReturn(
                Either.right(BehandlingTestUtils.microsoftGraphMock.response),
                MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
            )
        }

        val journalførIverksettingServiceMock = mock<JournalførIverksettingService>()

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService>()

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            journalførIverksettingService = journalførIverksettingServiceMock,
            oppgaveService = oppgaveServiceMock,

        ).ferdigstillInnvilgelse(utbetalingId)

        actual shouldBe Unit

        inOrder(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            personServiceMock,
            oppslagMock,
            journalførIverksettingServiceMock,
            oppgaveServiceMock
        ) {
            verify(behandlingRepoMock).hentBehandlingForUtbetaling(argThat { it shouldBe utbetalingId })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            argumentCaptor<NavIdentBruker>().apply {
                verify(oppslagMock, times(2)).hentBrukerinformasjonForNavIdent(capture())
                firstValue shouldBe saksbehandler
                secondValue shouldBe attestant
            }
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe iverksattOppgaveId })
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
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandlingForUtbetaling(any()) } doReturn innvilgetBehandlingUtenJournalpost
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn Either.right(BehandlingTestUtils.microsoftGraphMock.response)
        }

        val journalførIverksettingServiceMock = mock<JournalførIverksettingService> {
            on {
                opprettJournalpost(
                    any(),
                    any()
                )
            } doReturn KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev.left()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService>()

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            journalførIverksettingService = journalførIverksettingServiceMock,
            oppgaveService = oppgaveServiceMock,

        ).ferdigstillInnvilgelse(utbetalingId)

        actual shouldBe Unit

        inOrder(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            personServiceMock,
            oppslagMock,
            journalførIverksettingServiceMock,
            oppgaveServiceMock
        ) {
            verify(behandlingRepoMock).hentBehandlingForUtbetaling(argThat { it shouldBe utbetalingId })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
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
    fun `Kan ikke journalføre eller distribuere brev hvis journalføring feiler`() {

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandlingForUtbetaling(any()) } doReturn innvilgetBehandlingUtenJournalpost
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn Either.right(BehandlingTestUtils.microsoftGraphMock.response)
        }

        val journalførIverksettingServiceMock = mock<JournalførIverksettingService> {
            on {
                opprettJournalpost(
                    any(),
                    any()
                )
            } doReturn KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev.left()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService>()

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            journalførIverksettingService = journalførIverksettingServiceMock,
            oppgaveService = oppgaveServiceMock,

        ).ferdigstillInnvilgelse(utbetalingId)

        actual shouldBe Unit

        inOrder(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            personServiceMock,
            oppslagMock,
            journalførIverksettingServiceMock,
            oppgaveServiceMock
        ) {
            verify(behandlingRepoMock).hentBehandlingForUtbetaling(argThat { it shouldBe utbetalingId })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            argumentCaptor<NavIdentBruker>().apply {
                verify(oppslagMock, times(2)).hentBrukerinformasjonForNavIdent(capture())
                firstValue shouldBe saksbehandler
                secondValue shouldBe attestant
            }
            verify(journalførIverksettingServiceMock).opprettJournalpost(
                argThat { it shouldBe innvilgetBehandlingUtenJournalpost.copy() },
                argThat {
                    it shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        attestantNavn = BehandlingTestUtils.microsoftGraphMock.response.displayName,
                        saksbehandlerNavn = BehandlingTestUtils.microsoftGraphMock.response.displayName,
                        behandling = innvilgetBehandlingUtenJournalpost.copy()
                    )
                },
            )
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe iverksattOppgaveId })
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
    fun `Kunne ikke distribuere brev`() {
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandlingForUtbetaling(any()) } doReturn innvilgetBehandlingUtenJournalpost
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService> {
            on { distribuerBrev(any()) } doReturn DistribuerIverksettingsbrevService.KunneIkkeDistribuereBrev.left()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn Either.right(BehandlingTestUtils.microsoftGraphMock.response)
        }

        val journalførIverksettingServiceMock = mock<JournalførIverksettingService> {
            on { opprettJournalpost(any(), any()) } doReturn iverksattJournalpostId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            journalførIverksettingService = journalførIverksettingServiceMock,
            oppgaveService = oppgaveServiceMock,
            distribuerIverksettingsbrevService = distribuerIverksettingsbrevServiceMock,
        ).ferdigstillInnvilgelse(utbetalingId)

        actual shouldBe Unit

        inOrder(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            personServiceMock,
            oppslagMock,
            journalførIverksettingServiceMock,
            oppgaveServiceMock
        ) {
            verify(behandlingRepoMock).hentBehandlingForUtbetaling(argThat { it shouldBe utbetalingId })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            argumentCaptor<NavIdentBruker>().apply {
                verify(oppslagMock, times(2)).hentBrukerinformasjonForNavIdent(capture())
                firstValue shouldBe saksbehandler
                secondValue shouldBe attestant
            }
            verify(journalførIverksettingServiceMock).opprettJournalpost(
                argThat { it shouldBe innvilgetBehandlingUtenJournalpost.copy() },
                argThat {
                    it shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        attestantNavn = BehandlingTestUtils.microsoftGraphMock.response.displayName,
                        saksbehandlerNavn = BehandlingTestUtils.microsoftGraphMock.response.displayName,
                        behandling = innvilgetBehandlingUtenJournalpost.copy()
                    )
                },
            )
            verify(distribuerIverksettingsbrevServiceMock).distribuerBrev(
                argThat {
                    it shouldBe innvilgetBehandlingUtenJournalpost
                }
            )
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe iverksattOppgaveId })
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
    fun `journalfører og distribuerer brev for iverksatt innvilget`() {
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandlingForUtbetaling(any()) } doReturn innvilgetBehandlingUtenJournalpost
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService> {
            on { distribuerBrev(any()) } doReturn innvilgetBehandlingUtenJournalpost.copy(
                iverksattBrevbestillingId = iverksattBrevbestillingId
            ).right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn Either.right(BehandlingTestUtils.microsoftGraphMock.response)
        }

        val journalførIverksettingServiceMock = mock<JournalførIverksettingService> {
            on { opprettJournalpost(any(), any()) } doReturn iverksattJournalpostId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            journalførIverksettingService = journalførIverksettingServiceMock,
            oppgaveService = oppgaveServiceMock,
            distribuerIverksettingsbrevService = distribuerIverksettingsbrevServiceMock
        ).ferdigstillInnvilgelse(utbetalingId)

        actual shouldBe Unit

        inOrder(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            personServiceMock,
            oppslagMock,
            journalførIverksettingServiceMock,
            oppgaveServiceMock
        ) {
            verify(behandlingRepoMock).hentBehandlingForUtbetaling(argThat { it shouldBe utbetalingId })
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            argumentCaptor<NavIdentBruker>().apply {
                verify(oppslagMock, times(2)).hentBrukerinformasjonForNavIdent(capture())
                firstValue shouldBe saksbehandler
                secondValue shouldBe attestant
            }
            verify(journalførIverksettingServiceMock).opprettJournalpost(
                argThat { it shouldBe innvilgetBehandlingUtenJournalpost.copy() },
                argThat {
                    it shouldBe LagBrevRequest.InnvilgetVedtak(
                        person = person,
                        attestantNavn = BehandlingTestUtils.microsoftGraphMock.response.displayName,
                        saksbehandlerNavn = BehandlingTestUtils.microsoftGraphMock.response.displayName,
                        behandling = innvilgetBehandlingUtenJournalpost.copy()
                    )
                },
            )
            verify(distribuerIverksettingsbrevServiceMock).distribuerBrev(
                argThat {
                    it shouldBe innvilgetBehandlingUtenJournalpost
                }
            )
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe iverksattOppgaveId })
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

    private fun createService(
        behandlingRepo: BehandlingRepo = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
        microsoftGraphApiOppslag: MicrosoftGraphApiOppslag = mock(),
        journalførIverksettingService: JournalførIverksettingService = mock(),
        distribuerIverksettingsbrevService: DistribuerIverksettingsbrevService = mock(),
    ) = FerdigstillIverksettingService(
        behandlingRepo = behandlingRepo,
        oppgaveService = oppgaveService,
        personService = personService,
        behandlingMetrics = behandlingMetrics,
        microsoftGraphApiClient = microsoftGraphApiOppslag,
        journalførIverksettingService = journalførIverksettingService,
        distribuerIverksettingsbrevService = distribuerIverksettingsbrevService,
    )
}
