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
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.Ident
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Person
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageBrev
import no.nav.su.se.bakover.service.fixedTidspunkt
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class SøknadsbehandlingServiceBrevTest {
    private val stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021)))
    private val beregnetAvslag = Søknadsbehandling.TilAttestering.Innvilget(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
        søknad = mock(),
        sakId = UUID.randomUUID(),
        saksnummer = Saksnummer(2021),
        fnr = FnrGenerator.random(),
        oppgaveId = OppgaveId("0"),
        beregning = TestBeregning,
        simulering = mock(),
        saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
        fritekstTilBrev = "",
        stønadsperiode = stønadsperiode,
        grunnlagsdata = Grunnlagsdata(
            bosituasjon = listOf(
                Grunnlag.Bosituasjon.Fullstendig.Enslig(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    periode = RevurderingTestUtils.periode,
                    begrunnelse = null,
                ),
            ),
        ),
        vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
    )

    private val vilkårsvurdertUavklartSøknadsbehandling = Søknadsbehandling.Vilkårsvurdert.Uavklart(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        sakId = UUID.randomUUID(),
        saksnummer = Saksnummer(2021),
        søknad = mock(),
        oppgaveId = OppgaveId("0"),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
        fnr = FnrGenerator.random(),
        fritekstTilBrev = "",
        stønadsperiode = stønadsperiode,
        grunnlagsdata = Grunnlagsdata.EMPTY,
        vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
    )

    private val person = Person(
        ident = Ident(
            fnr = FnrGenerator.random(),
            aktørId = AktørId(aktørId = "")
        ),
        navn = Person.Navn(fornavn = "", mellomnavn = null, etternavn = ""),
    )

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
        ).brev(SøknadsbehandlingService.BrevRequest.UtenFritekst(beregnetAvslag)).let {
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
            on { hentNavnForNavIdent(any()) } doReturn MicrosoftGraphApiOppslagFeil.FantIkkeBrukerForNavIdent.left()
        }

        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = microsoftGraphApiOppslagMock
        ).brev(SøknadsbehandlingService.BrevRequest.UtenFritekst(beregnetAvslag)).let {
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
            on { hentNavnForNavIdent(any()) } doReturn BehandlingTestUtils.microsoftGraphMock.response.displayName.right()
        }

        val brevServiceMock = mock<BrevService> {
            on { lagBrev(any()) } doReturn KunneIkkeLageBrev.KunneIkkeGenererePDF.left()
        }

        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            personService = personServiceMock,
            microsoftGraphApiOppslag = microsoftGraphApiOppslagMock,
            brevService = brevServiceMock
        ).brev(SøknadsbehandlingService.BrevRequest.UtenFritekst(beregnetAvslag)).let {
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
            on { hentNavnForNavIdent(any()) } doReturn BehandlingTestUtils.microsoftGraphMock.response.displayName.right()
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
        ).brev(SøknadsbehandlingService.BrevRequest.UtenFritekst(beregnetAvslag)).let {
            it shouldBe pdf.right()
        }
    }

    @Test
    fun `kaster exception hvis det ikke er mulig å opprette brev for aktuell behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn vilkårsvurdertUavklartSøknadsbehandling
        }

        assertThrows<LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans> {
            createSøknadsbehandlingService(
                søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            ).brev(SøknadsbehandlingService.BrevRequest.UtenFritekst(vilkårsvurdertUavklartSøknadsbehandling))
        }
    }
}
