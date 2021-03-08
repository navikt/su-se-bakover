package no.nav.su.se.bakover.service.vedtak

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doReturnConsecutively
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.client.person.MicrosoftGraphResponse
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
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

internal class FerdigstillVedtakServiceImplTest {

    @Test
    fun `svarer med feil hvis man ikke finner person for journalpost`() {
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val vedtak = avslagsVedtak()

        val response = createService(
            personService = personServiceMock,
        ).journalførOgLagre(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FantIkkePerson.left()

        inOrder(personServiceMock) {
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
        }
    }

    @Test
    fun `svarer med feil hvis man ikke finner saksbehandler for journalpost`() {
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
        }

        val vedtak = avslagsVedtak()

        val response = createService(
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslagMock
        ).journalførOgLagre(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FantIkkeNavnPåSaksbehandlerEllerAttestant.left()

        inOrder(
            personServiceMock,
            microsoftGraphApiOppslagMock
        ) {
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
            verify(microsoftGraphApiOppslagMock).hentBrukerinformasjonForNavIdent(argThat { it shouldBe vedtak.saksbehandler })
        }
    }

    @Test
    fun `svarer med feil hvis man ikke finner attestant for journalpost`() {
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturnConsecutively listOf(
                graphApiResponse.right(),
                MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
            )
        }

        val vedtak = avslagsVedtak()

        val response = createService(
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslagMock
        ).journalførOgLagre(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FantIkkeNavnPåSaksbehandlerEllerAttestant.left()

        inOrder(
            personServiceMock,
            microsoftGraphApiOppslagMock
        ) {
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
            verify(microsoftGraphApiOppslagMock, times(2)).hentBrukerinformasjonForNavIdent(any())
        }
    }

    @Test
    fun `svarer med feil dersom journalføring av brev feiler`() {
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn graphApiResponse.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn KunneIkkeJournalføreBrev.KunneIkkeOppretteJournalpost.left()
        }

        val vedtak = avslagsVedtak()

        val response = createService(
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslagMock,
            brevService = brevServiceMock
        ).journalførOgLagre(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.FeilVedJournalføring.left()

        inOrder(
            personServiceMock,
            microsoftGraphApiOppslagMock,
            brevServiceMock
        ) {
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
            verify(microsoftGraphApiOppslagMock, times(2)).hentBrukerinformasjonForNavIdent(any())
            verify(brevServiceMock).journalførBrev(any(), any())
        }
    }

    @Test
    fun `svarer med feil dersom journalføring av brev allerede er utført`() {
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn graphApiResponse.right()
        }

        val vedtakRepoMock = mock<VedtakRepo>()
        val brevServiceMock = mock<BrevService>()

        val vedtak = journalførtAvslagsVedtak()

        val response = createService(
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslagMock,
            vedtakRepo = vedtakRepoMock,
            brevService = brevServiceMock
        ).journalførOgLagre(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeJournalføreBrev.AlleredeJournalført(iverksattJournalpostId).left()

        inOrder(
            personServiceMock,
            microsoftGraphApiOppslagMock,
            vedtakRepoMock
        ) {
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
            verify(microsoftGraphApiOppslagMock, times(2)).hentBrukerinformasjonForNavIdent(any())
            verifyZeroInteractions(vedtakRepoMock, brevServiceMock)
        }
    }

    @Test
    fun `oppdaterer vedtak og lagrer dersom journalføring går fint`() {
        val personServiceMock = mock<PersonService> {
            on { hentPersonMedSystembruker(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn graphApiResponse.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { journalførBrev(any(), any()) } doReturn iverksattJournalpostId.right()
        }

        val vedtakRepoMock = mock<VedtakRepo>()

        val vedtak = avslagsVedtak()

        val response = createService(
            personService = personServiceMock,
            microsoftGraphApiClient = microsoftGraphApiOppslagMock,
            brevService = brevServiceMock,
            vedtakRepo = vedtakRepoMock
        ).journalførOgLagre(vedtak)

        response shouldBe vedtak.copy(eksterneIverksettingsteg = JournalføringOgBrevdistribusjon.Journalført(iverksattJournalpostId)).right()

        inOrder(
            personServiceMock,
            microsoftGraphApiOppslagMock,
            brevServiceMock,
            vedtakRepoMock
        ) {
            verify(personServiceMock).hentPersonMedSystembruker(argThat { it shouldBe vedtak.behandling.fnr })
            verify(microsoftGraphApiOppslagMock, times(2)).hentBrukerinformasjonForNavIdent(any())
            verify(brevServiceMock).journalførBrev(any(), any())
            verify(vedtakRepoMock).lagre(vedtak.copy(eksterneIverksettingsteg = JournalføringOgBrevdistribusjon.Journalført(iverksattJournalpostId)))
        }
    }

    @Test
    fun `svarer med feil dersom brevdistribusjon feiler`() {
        val brevServiceMock = mock<BrevService> {
            on { distribuerBrev(any()) } doReturn KunneIkkeDistribuereBrev.left()
        }

        val vedtak = journalførtAvslagsVedtak()

        val response = createService(
            brevService = brevServiceMock,
        ).distribuerOgLagre(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev.FeilVedDistribusjon(iverksattJournalpostId).left()

        inOrder(brevServiceMock) {
            verify(brevServiceMock).distribuerBrev(iverksattJournalpostId)
        }
    }

    @Test
    fun `svarer med feil dersom distribusjon ikke er journalført først`() {
        val vedtakRepoMock = mock<VedtakRepo>()
        val brevServiceMock = mock<BrevService>()

        val vedtak = avslagsVedtak()

        val response = createService(
            vedtakRepo = vedtakRepoMock,
            brevService = brevServiceMock,
        ).distribuerOgLagre(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev.MåJournalføresFørst.left()

        inOrder(
            vedtakRepoMock,
            brevServiceMock
        ) {
            verifyZeroInteractions(vedtakRepoMock, brevServiceMock)
        }
    }

    @Test
    fun `svarer med feil dersom distribusjon av brev allerede er utført`() {
        val vedtakRepoMock = mock<VedtakRepo>()
        val brevServiceMock = mock<BrevService>()

        val vedtak = journalførtOgDistribuertAvslagsVedtak()

        val response = createService(
            vedtakRepo = vedtakRepoMock,
            brevService = brevServiceMock,
        ).distribuerOgLagre(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeDistribuereBrev.AlleredeDistribuert(iverksattJournalpostId).left()

        inOrder(
            vedtakRepoMock,
            brevServiceMock
        ) {
            verifyZeroInteractions(vedtakRepoMock, brevServiceMock)
        }
    }

    @Test
    fun `oppdaterer vedtak og lagrer dersom brevdistribusjon går fint`() {
        val brevServiceMock = mock<BrevService> {
            on { distribuerBrev(any()) } doReturn iverksattBrevbestillingId.right()
        }

        val vedtakRepoMock = mock<VedtakRepo>()

        val vedtak = journalførtAvslagsVedtak()

        val response = createService(
            brevService = brevServiceMock,
            vedtakRepo = vedtakRepoMock
        ).distribuerOgLagre(vedtak)

        response shouldBe vedtak.copy(eksterneIverksettingsteg = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(iverksattJournalpostId, iverksattBrevbestillingId)).right()

        inOrder(
            brevServiceMock,
            vedtakRepoMock,
        ) {
            verify(brevServiceMock).distribuerBrev(this@FerdigstillVedtakServiceImplTest.iverksattJournalpostId)
            verify(vedtakRepoMock).lagre(vedtak.copy(eksterneIverksettingsteg = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(iverksattJournalpostId, iverksattBrevbestillingId)))
        }
    }

    @Test
    fun `svarer med feil dersom lukking av oppgave feiler`() {
        val oppgaveServiceMock = mock<OppgaveService> {
            on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
        }

        val vedtak = journalførtOgDistribuertAvslagsVedtak()

        val response = createService(
            oppgaveService = oppgaveServiceMock
        ).lukkOppgave(vedtak)

        response shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave.left()

        inOrder(
            oppgaveServiceMock,
        ) {
            verify(oppgaveServiceMock).lukkOppgave(oppgaveId)
        }
    }

    private fun createService(
        oppgaveService: OppgaveService = mock(),
        personService: PersonService = mock(),
        clock: Clock = fixedClock,
        microsoftGraphApiClient: MicrosoftGraphApiOppslag = mock(),
        brevService: BrevService = mock(),
        vedtakRepo: VedtakRepo = mock()
    ) = FerdigstillVedtakServiceImpl(
        oppgaveService = oppgaveService,
        personService = personService,
        clock = clock,
        microsoftGraphApiOppslag = microsoftGraphApiClient,
        brevService = brevService,
        vedtakRepo = vedtakRepo
    )

    private fun avslagsVedtak() =
        Vedtak.AvslåttStønad.fromSøknadsbehandlingMedBeregning(
            Søknadsbehandling.Iverksatt.Avslag.MedBeregning(
                id = UUID.randomUUID(),
                opprettet = Tidspunkt.now(),
                søknad = Søknad.Journalført.MedOppgave(
                    id = BehandlingTestUtils.søknadId,
                    opprettet = Tidspunkt.EPOCH,
                    sakId = UUID.randomUUID(),
                    søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                    oppgaveId = BehandlingTestUtils.søknadOppgaveId,
                    journalpostId = BehandlingTestUtils.søknadJournalpostId
                ),
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
                sakId = UUID.randomUUID(),
                saksnummer = Saksnummer(1),
                fnr = FnrGenerator.random(),
                saksbehandler = NavIdentBruker.Saksbehandler("saks"),
                oppgaveId = oppgaveId,
                beregning = TestBeregning,
                attestering = Attestering.Iverksatt(NavIdentBruker.Attestant("atte"))
            )
        )

    private fun journalførtAvslagsVedtak() = avslagsVedtak().copy(eksterneIverksettingsteg = JournalføringOgBrevdistribusjon.Journalført(iverksattJournalpostId))
    private fun journalførtOgDistribuertAvslagsVedtak() = avslagsVedtak().copy(eksterneIverksettingsteg = JournalføringOgBrevdistribusjon.JournalførtOgDistribuertBrev(iverksattJournalpostId, iverksattBrevbestillingId))

    private val person = Person(
        ident = Ident(
            fnr = BehandlingTestUtils.fnr,
            aktørId = AktørId(aktørId = "123")
        ),
        navn = Person.Navn(fornavn = "Tore", mellomnavn = "Johnas", etternavn = "Strømøy")
    )
    private val iverksattJournalpostId = JournalpostId("j")
    private val iverksattBrevbestillingId = BrevbestillingId("b")
    private val oppgaveId = OppgaveId("2")

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
}
