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
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import org.junit.jupiter.api.Test
import org.mockito.internal.verification.Times
import java.util.UUID

internal class OpprettManglendeJournalpostOgBrevForIverksettingerTest {
    private val fnr = FnrGenerator.random()
    private val person = Person(
        ident = Ident(
            fnr = fnr,
            aktørId = AktørId(aktørId = "123")
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy"),
        telefonnummer = null,
        adresse = null,
        statsborgerskap = null,
        kjønn = null,
        adressebeskyttelse = null,
        skjermet = null,
        kontaktinfo = null,
        vergemål = null,
        fullmakt = null,
    )
    private val sakIdJournalpost = UUID.randomUUID()
    private val sakIdBestiltBrev = UUID.randomUUID()

    private val behandlingIdJournalpost = UUID.randomUUID()
    private val behandlingIdBestiltBrev = UUID.randomUUID()

    private val journalpostId = JournalpostId("1")
    private val journalpostIdBestiltBrev = JournalpostId("1")

    private val brevbestillingId = BrevbestillingId("2")

    private val journalpostOgBrevdistribusjonResultat =
        OpprettetJournalpostForIverksetting(sakIdJournalpost, behandlingIdJournalpost, journalpostId)
    private val bestiltBrev =
        BestiltBrev(sakIdBestiltBrev, behandlingIdBestiltBrev, journalpostIdBestiltBrev, brevbestillingId)

    private val innvilgetBehandlingUtenJournalpost = BehandlingFactory(mock()).createBehandling(
        søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakIdJournalpost,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = OppgaveId("1"),
            journalpostId = journalpostId
        ),
        id = behandlingIdJournalpost,
        status = Behandling.BehandlingsStatus.IVERKSATT_INNVILGET,
        saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
        attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("attestant")),
        sakId = sakIdJournalpost,
        saksnummer = Saksnummer(1),
        fnr = fnr,
        oppgaveId = OppgaveId("1")
    )

    @Test
    fun `Gjør ingenting hvis det ikke er noe å gjøre`() {
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentIverksatteBehandlingerUtenJournalposteringer() } doReturn emptyList()
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn emptyList()
        }

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe OpprettManglendeJournalpostOgBrevdistribusjonResultat(
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
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentIverksatteBehandlingerUtenJournalposteringer() } doReturn listOf(innvilgetBehandlingUtenJournalpost)
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn emptyList()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn FantIkkeBrukerForNavIdent.left()
        }

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = listOf(
                KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = sakIdJournalpost,
                    behandlingId = behandlingIdJournalpost,
                    grunn = "Kunne ikke hente saksbehandlers navn"
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
            verify(oppslagMock).hentBrukerinformasjonForNavIdent(any())

            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenBrevbestillinger()
        }
        verifyNoMoreInteractions(behandlingRepoMock, personServiceMock, oppslagMock)
    }

    @Test
    fun `Kunne ikke opprette journalpost hvis vi ikke finner attestant`() {
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentIverksatteBehandlingerUtenJournalposteringer() } doReturn listOf(innvilgetBehandlingUtenJournalpost)
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn emptyList()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) }.doReturn(
                BehandlingTestUtils.microsoftGraphMock.response.right(),
                FantIkkeBrukerForNavIdent.left()
            )
        }

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = listOf(
                KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = sakIdJournalpost,
                    behandlingId = behandlingIdJournalpost,
                    grunn = "Kunne ikke hente attestants navn"
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
            argumentCaptor<NavIdentBruker>().apply {
                verify(oppslagMock, times(2)).hentBrukerinformasjonForNavIdent(capture())
                firstValue shouldBe NavIdentBruker.Saksbehandler("saksbehandler")
                secondValue shouldBe NavIdentBruker.Attestant("attestant")
            }
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenBrevbestillinger()
        }
        verifyNoMoreInteractions(behandlingRepoMock, personServiceMock, oppslagMock)
    }

    @Test
    fun `Kunne ikke opprette journalpost hvis vi ikke finner person`() {
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentIverksatteBehandlingerUtenJournalposteringer() } doReturn listOf(innvilgetBehandlingUtenJournalpost)
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn emptyList()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn Either.right(BehandlingTestUtils.microsoftGraphMock.response)
        }

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = listOf(
                KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = sakIdJournalpost,
                    behandlingId = behandlingIdJournalpost,
                    grunn = "Kunne ikke hente person"
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
            verify(oppslagMock, Times(2)).hentBrukerinformasjonForNavIdent(any())
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })

            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenBrevbestillinger()
        }
        verifyNoMoreInteractions(behandlingRepoMock, personServiceMock, oppslagMock)
    }

    @Test
    fun `Kunne ikke journalføre brev når vi ikke klarer å lage det`() {
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentIverksatteBehandlingerUtenJournalposteringer() } doReturn listOf(innvilgetBehandlingUtenJournalpost)
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn emptyList()
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val journalførIverksettingServiceMock = mock<JournalførIverksettingService> {
            on {
                opprettJournalpost(
                    any(),
                    any()
                )
            } doReturn KunneIkkeIverksetteBehandling.KunneIkkeJournalføreBrev.left()
        }

        val brevServiceMock = mock<DistribuerIverksettingsbrevService>()

        val oppslagMock = mock<MicrosoftGraphApiOppslag> {
            on {
                hentBrukerinformasjonForNavIdent(any())
            } doReturn Either.right(BehandlingTestUtils.microsoftGraphMock.response)
        }

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            distribuerIverksettingsbrevService = brevServiceMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            journalførIverksettingService = journalførIverksettingServiceMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = listOf(
                KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = sakIdJournalpost,
                    behandlingId = behandlingIdJournalpost,
                    grunn = "Kunne ikke opprette journalpost mot eksternt system"
                ).left()
            ),
            brevbestillingsresultat = emptyList()
        )

        inOrder(
            behandlingRepoMock,
            brevServiceMock,
            personServiceMock,
            oppslagMock,
            journalførIverksettingServiceMock
        ) {
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenJournalposteringer()
            verify(oppslagMock, Times(2)).hentBrukerinformasjonForNavIdent(any())
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(journalførIverksettingServiceMock).opprettJournalpost(any(), any())

            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenBrevbestillinger()
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            brevServiceMock,
            personServiceMock,
            oppslagMock,
            journalførIverksettingServiceMock
        )
    }

    @Test
    fun `Kan ikke bestille brev hvis iverksattJournalId er null`() {
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentIverksatteBehandlingerUtenJournalposteringer() } doReturn emptyList()
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn listOf(
                innvilgetBehandlingUtenJournalpost.copy(
                    id = behandlingIdBestiltBrev,
                    sakId = sakIdBestiltBrev,
                    iverksattJournalpostId = null
                )
            )
        }

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = emptyList(),
            brevbestillingsresultat = listOf(
                KunneIkkeBestilleBrev(
                    sakId = sakIdBestiltBrev,
                    behandlingId = behandlingIdBestiltBrev,
                    journalpostId = null,
                    grunn = "Kunne ikke opprette brevbestilling siden iverksattJournalpostId er null."
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
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentIverksatteBehandlingerUtenJournalposteringer() } doReturn emptyList()
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn listOf(
                innvilgetBehandlingUtenJournalpost.copy(
                    id = behandlingIdBestiltBrev,
                    sakId = sakIdBestiltBrev,
                    iverksattJournalpostId = journalpostIdBestiltBrev
                )
            )
        }

        val journalførIverksettingServiceMock = mock<JournalførIverksettingService>()

        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService> {
            on { distribuerBrev(any()) } doReturn DistribuerIverksettingsbrevService.KunneIkkeDistribuereBrev.left()
        }

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            distribuerIverksettingsbrevService = distribuerIverksettingsbrevServiceMock,
            journalførIverksettingService = journalførIverksettingServiceMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = emptyList(),
            brevbestillingsresultat = listOf(
                KunneIkkeBestilleBrev(
                    sakId = sakIdBestiltBrev,
                    behandlingId = behandlingIdBestiltBrev,
                    journalpostId = journalpostIdBestiltBrev,
                    grunn = "Kunne ikke bestille brev"
                ).left()
            )
        )

        inOrder(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            journalførIverksettingServiceMock
        ) {
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenJournalposteringer()
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenBrevbestillinger()
            verify(distribuerIverksettingsbrevServiceMock).distribuerBrev(
                argThat {
                    it shouldBe innvilgetBehandlingUtenJournalpost.copy(
                        id = behandlingIdBestiltBrev,
                        sakId = sakIdBestiltBrev,
                        iverksattJournalpostId = journalpostIdBestiltBrev,
                        iverksattBrevbestillingId = null,
                    )
                }
            )
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            journalførIverksettingServiceMock
        )
    }

    @Test
    fun `distribuerer brev for iverksatt avlsag`() {
        val journalførtBehandling = innvilgetBehandlingUtenJournalpost.copy(
            id = behandlingIdBestiltBrev,
            sakId = sakIdBestiltBrev,
            iverksattJournalpostId = journalpostIdBestiltBrev,
            status = Behandling.BehandlingsStatus.IVERKSATT_AVSLAG,
        )
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentIverksatteBehandlingerUtenJournalposteringer() } doReturn listOf(
                innvilgetBehandlingUtenJournalpost.copy(
                    status = Behandling.BehandlingsStatus.IVERKSATT_AVSLAG
                )
            )
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn listOf(
                journalførtBehandling,
            )
        }

        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService> {
            on { distribuerBrev(any()) } doReturn innvilgetBehandlingUtenJournalpost.copy(
                iverksattBrevbestillingId = brevbestillingId
            ).right()
        }

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            distribuerIverksettingsbrevService = distribuerIverksettingsbrevServiceMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = listOf(
                KunneIkkeOppretteJournalpostForIverksetting(
                    sakId = innvilgetBehandlingUtenJournalpost.sakId,
                    behandlingId = innvilgetBehandlingUtenJournalpost.id,
                    grunn = "Kunne ikke opprette journalpost for status IVERKSATT_AVSLAG"
                ).left()
            ),
            brevbestillingsresultat = listOf(bestiltBrev.right())
        )

        inOrder(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
        ) {
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenJournalposteringer()

            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenBrevbestillinger()
            verify(distribuerIverksettingsbrevServiceMock).distribuerBrev(
                argThat { it shouldBe journalførtBehandling }
            )
        }
        verifyNoMoreInteractions(behandlingRepoMock, distribuerIverksettingsbrevServiceMock)
    }

    @Test
    fun `journalfører og distribuerer brev for iverksatt innvilget`() {
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentIverksatteBehandlingerUtenJournalposteringer() } doReturn listOf(innvilgetBehandlingUtenJournalpost)
            on { hentIverksatteBehandlingerUtenBrevbestillinger() } doReturn listOf(
                innvilgetBehandlingUtenJournalpost.copy(
                    id = behandlingIdBestiltBrev,
                    sakId = sakIdBestiltBrev,
                    iverksattJournalpostId = journalpostIdBestiltBrev
                ),
            )
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val journalførIverksettingServiceMock = mock<JournalførIverksettingService> {
            on { opprettJournalpost(any(), any()) } doReturn journalpostId.right()
        }

        val distribuerIverksettingsbrevServiceMock = mock<DistribuerIverksettingsbrevService> {
            // on { journalførBrev(any(), any()) } doReturn journalpostId.right()
            on { distribuerBrev(any()) } doReturn innvilgetBehandlingUtenJournalpost.copy(
                iverksattBrevbestillingId = brevbestillingId
            ).right()
        }

        val oppslagMock: MicrosoftGraphApiOppslag = mock {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn Either.right(BehandlingTestUtils.microsoftGraphMock.response)
        }

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            distribuerIverksettingsbrevService = distribuerIverksettingsbrevServiceMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = oppslagMock,
            journalførIverksettingService = journalførIverksettingServiceMock,
        ).opprettManglendeJournalpostOgBrevdistribusjon()

        actual shouldBe OpprettManglendeJournalpostOgBrevdistribusjonResultat(
            journalpostresultat = listOf(journalpostOgBrevdistribusjonResultat.right()),
            brevbestillingsresultat = listOf(bestiltBrev.right())
        )

        inOrder(
            behandlingRepoMock,
            personServiceMock,
            oppslagMock,
            journalførIverksettingServiceMock,
            distribuerIverksettingsbrevServiceMock,
        ) {
            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenJournalposteringer()
            verify(oppslagMock, Times(2)).hentBrukerinformasjonForNavIdent(any())
            verify(personServiceMock).hentPerson(argThat { it shouldBe fnr })
            verify(journalførIverksettingServiceMock).opprettJournalpost(any(), any())

            verify(behandlingRepoMock).hentIverksatteBehandlingerUtenBrevbestillinger()
            verify(distribuerIverksettingsbrevServiceMock).distribuerBrev(any())
        }
        verifyNoMoreInteractions(
            behandlingRepoMock,
            distribuerIverksettingsbrevServiceMock,
            personServiceMock,
            oppslagMock
        )
    }

    private fun createService(
        behandlingRepo: BehandlingRepo = mock(),
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
        microsoftGraphApiOppslag: MicrosoftGraphApiOppslag = mock(),
        journalførIverksettingService: JournalførIverksettingService = mock(),
        distribuerIverksettingsbrevService: DistribuerIverksettingsbrevService = mock()
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
