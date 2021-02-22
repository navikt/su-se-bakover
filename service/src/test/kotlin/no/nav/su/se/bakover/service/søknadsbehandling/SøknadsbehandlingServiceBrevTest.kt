package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslag
import no.nav.su.se.bakover.client.person.MicrosoftGraphApiOppslagFeil
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknadsbehandling.BehandlingsStatus
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.person.PersonService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class SøknadsbehandlingServiceBrevTest {

    private val beregnetAvslag = Søknadsbehandling.TilAttestering.Innvilget(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
        søknad = mock(),
        sakId = UUID.randomUUID(),
        saksnummer = Saksnummer(0),
        fnr = FnrGenerator.random(),
        oppgaveId = OppgaveId("0"),
        beregning = TestBeregning,
        simulering = mock(),
        saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler")
    )

    private val person = Person(
        ident = Ident(
            fnr = FnrGenerator.random(),
            aktørId = AktørId(aktørId = "")
        ),
        navn = Person.Navn(fornavn = "", mellomnavn = null, etternavn = ""),
    )

    @Test
    fun `svarer med feil hvis vi ikke finner behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }
        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).brev(SøknadsbehandlingService.BrevRequest(beregnetAvslag.id)).let {
            it shouldBe SøknadsbehandlingService.KunneIkkeLageBrev.FantIkkeBehandling.left()
        }
    }

    @Test
    fun `svarer med feil hvis vi ikke finner person`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn beregnetAvslag
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            personService = personServiceMock
        ).brev(SøknadsbehandlingService.BrevRequest(beregnetAvslag.id)).let {
            it shouldBe SøknadsbehandlingService.KunneIkkeLageBrev.FantIkkePerson.left()
        }
    }

    @Test
    fun `svarer med feil hvis vi ikke finner navn på attestant eller saksbehandler`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn beregnetAvslag
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
        }

        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = microsoftGraphApiOppslagMock
        ).brev(SøknadsbehandlingService.BrevRequest(beregnetAvslag.id)).let {
            it shouldBe SøknadsbehandlingService.KunneIkkeLageBrev.FikkIkkeHentetSaksbehandlerEllerAttestant.left()
        }
    }

    @Test
    fun `svarer med feil hvis generering av pdf feiler`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn beregnetAvslag
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn BehandlingTestUtils.microsoftGraphMock.response.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        }

        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = microsoftGraphApiOppslagMock,
            brevService = brevServiceMock
        ).brev(SøknadsbehandlingService.BrevRequest(beregnetAvslag.id)).let {
            it shouldBe SøknadsbehandlingService.KunneIkkeLageBrev.KunneIkkeLagePDF.left()
        }
    }

    @Test
    fun `svarer med byte array dersom alt går fint`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn beregnetAvslag
        }

        val personServiceMock = mock<PersonService> {
            on { hentPerson(any()) } doReturn person.right()
        }

        val microsoftGraphApiOppslagMock = mock<MicrosoftGraphApiOppslag> {
            on { hentBrukerinformasjonForNavIdent(any()) } doReturn BehandlingTestUtils.microsoftGraphMock.response.right()
        }

        val pdf = "".toByteArray()
        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn pdf.right()
        }

        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = microsoftGraphApiOppslagMock,
            brevService = brevServiceMock
        ).brev(SøknadsbehandlingService.BrevRequest(beregnetAvslag.id)).let {
            it shouldBe pdf.right()
        }
    }

    @Test
    fun `kaster exception hvis det ikke er mulig å opprette brev for aktuell behandling`() {
        val behandlingMock = mock<Søknadsbehandling.Vilkårsvurdert.Uavklart> {
            on { accept(any()) }.thenCallRealMethod()
            on { status } doReturn BehandlingsStatus.OPPRETTET
        }

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandlingMock
        }

        assertThrows<LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstansException> {
            createSøknadsbehandlingService(
                søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            ).brev(SøknadsbehandlingService.BrevRequest(beregnetAvslag.id))
        }
    }
}
