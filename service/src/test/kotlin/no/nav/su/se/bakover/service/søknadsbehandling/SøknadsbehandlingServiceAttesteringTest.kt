package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.ValgtStønadsperiode
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.junit.jupiter.api.Test
import java.util.UUID

class SøknadsbehandlingServiceAttesteringTest {

    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(0)
    private val søknadId = UUID.randomUUID()
    private val oppgaveId = OppgaveId("o")
    private val fnr = FnrGenerator.random()
    private val nyOppgaveId = OppgaveId("999")
    private val aktørId = AktørId("12345")
    private val periode = Periode.create(1.januar(2021), 31.desember(2021))
    private val stønadsperiode = ValgtStønadsperiode(periode, "")
    private val simulertBehandling = Søknadsbehandling.Simulert(
        id = UUID.randomUUID(),
        opprettet = Tidspunkt.now(),
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
        søknad = Søknad.Journalført.MedOppgave(
            id = søknadId,
            opprettet = Tidspunkt.now(),
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = oppgaveId,
            journalpostId = JournalpostId("j")
        ),
        beregning = TestBeregning,
        simulering = Simulering(
            gjelderId = fnr,
            gjelderNavn = "NAVN",
            datoBeregnet = idag(),
            nettoBeløp = 191500,
            periodeList = listOf()
        ),
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        oppgaveId = oppgaveId,
        fritekstTilBrev = "",
        stønadsperiode = stønadsperiode,
        grunnlagsdata = Grunnlagsdata.EMPTY,
        vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
    )

    private val saksbehandler = NavIdentBruker.Saksbehandler("Z12345")

    @Test
    fun `sjekk at vi sender inn riktig oppgaveId ved lukking av oppgave ved attestering`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn simulertBehandling
            on { hentEventuellTidligereAttestering(any()) } doReturn null
        }

        val personServiceMock: PersonService = mock {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn nyOppgaveId.right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val eventObserver: EventObserver = mock {
            on { handle(any()) }.doNothing()
        }

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            observer = eventObserver
        ).sendTilAttestering(SøknadsbehandlingService.SendTilAttesteringRequest(simulertBehandling.id, saksbehandler, ""))

        val expected = Søknadsbehandling.TilAttestering.Innvilget(
            id = simulertBehandling.id,
            opprettet = simulertBehandling.opprettet,
            behandlingsinformasjon = simulertBehandling.behandlingsinformasjon,
            søknad = simulertBehandling.søknad,
            beregning = simulertBehandling.beregning,
            simulering = simulertBehandling.simulering,
            sakId = simulertBehandling.sakId,
            saksnummer = simulertBehandling.saksnummer,
            fnr = simulertBehandling.fnr,
            oppgaveId = nyOppgaveId,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            stønadsperiode = simulertBehandling.stønadsperiode,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )

        actual shouldBe expected.right()

        inOrder(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver) {
            verify(søknadsbehandlingRepoMock).hent(simulertBehandling.id)
            verify(personServiceMock).hentAktørId(fnr)
            verify(søknadsbehandlingRepoMock).hentEventuellTidligereAttestering(simulertBehandling.id)
            verify(oppgaveServiceMock).opprettOppgave(
                config = OppgaveConfig.Attestering(
                    søknadId = søknadId,
                    aktørId = aktørId,
                    tilordnetRessurs = null
                )
            )
            verify(søknadsbehandlingRepoMock).lagre(expected)
            verify(oppgaveServiceMock).lukkOppgave(oppgaveId)
            verify(eventObserver).handle(argThat { it shouldBe Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingTilAttestering(expected) })
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    @Test
    fun `svarer med feil dersom man ikke finner behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val personServiceMock: PersonService = mock()
        val oppgaveServiceMock: OppgaveService = mock()
        val eventObserver: EventObserver = mock()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            observer = eventObserver
        ).sendTilAttestering(SøknadsbehandlingService.SendTilAttesteringRequest(simulertBehandling.id, saksbehandler, ""))

        actual shouldBe SøknadsbehandlingService.KunneIkkeSendeTilAttestering.FantIkkeBehandling.left()

        verify(søknadsbehandlingRepoMock).hent(simulertBehandling.id)

        verifyNoMoreInteractions(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    @Test
    fun `svarer med feil dersom man ikke finner aktørid for person`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn simulertBehandling
        }

        val personServiceMock: PersonService = mock {
            on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val oppgaveServiceMock = mock<OppgaveService>()

        val eventObserver: EventObserver = mock()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            observer = eventObserver
        ).sendTilAttestering(SøknadsbehandlingService.SendTilAttesteringRequest(simulertBehandling.id, saksbehandler, ""))

        actual shouldBe SøknadsbehandlingService.KunneIkkeSendeTilAttestering.KunneIkkeFinneAktørId.left()

        verify(søknadsbehandlingRepoMock).hent(simulertBehandling.id)
        verify(personServiceMock).hentAktørId(simulertBehandling.fnr)

        verifyNoMoreInteractions(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    @Test
    fun `svarer med feil dersom man ikke får til å opprette oppgave til attestant`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn simulertBehandling
            on { hentEventuellTidligereAttestering(simulertBehandling.id) } doReturn null
        }

        val personServiceMock: PersonService = mock {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }
        val oppgaveServiceMock: OppgaveService = mock {
            on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
        }
        val eventObserver: EventObserver = mock()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            observer = eventObserver
        ).sendTilAttestering(SøknadsbehandlingService.SendTilAttesteringRequest(simulertBehandling.id, saksbehandler, ""))

        actual shouldBe SøknadsbehandlingService.KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave.left()

        verify(søknadsbehandlingRepoMock).hent(simulertBehandling.id)
        verify(personServiceMock).hentAktørId(simulertBehandling.fnr)
        verify(søknadsbehandlingRepoMock).hentEventuellTidligereAttestering(simulertBehandling.id)
        verify(oppgaveServiceMock).opprettOppgave(
            OppgaveConfig.Attestering(
                søknadId = simulertBehandling.søknad.id,
                aktørId = aktørId,
                tilordnetRessurs = null
            )
        )

        verifyNoMoreInteractions(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    @Test
    fun `sender til attestering selv om lukking av eksisterende oppgave feiler`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn simulertBehandling
            on { hentEventuellTidligereAttestering(any()) } doReturn null
        }

        val personServiceMock: PersonService = mock {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn nyOppgaveId.right()
            on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
        }

        val eventObserver: EventObserver = mock {
            on { handle(any()) }.doNothing()
        }

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            observer = eventObserver
        ).sendTilAttestering(
            SøknadsbehandlingService.SendTilAttesteringRequest(simulertBehandling.id, saksbehandler, "")
        )

        val expected = Søknadsbehandling.TilAttestering.Innvilget(
            id = simulertBehandling.id,
            opprettet = simulertBehandling.opprettet,
            behandlingsinformasjon = simulertBehandling.behandlingsinformasjon,
            søknad = simulertBehandling.søknad,
            beregning = simulertBehandling.beregning,
            simulering = simulertBehandling.simulering,
            sakId = simulertBehandling.sakId,
            saksnummer = simulertBehandling.saksnummer,
            fnr = simulertBehandling.fnr,
            oppgaveId = nyOppgaveId,
            saksbehandler = saksbehandler,
            fritekstTilBrev = "",
            stønadsperiode = simulertBehandling.stønadsperiode,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )

        actual shouldBe expected.right()

        inOrder(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver) {
            verify(søknadsbehandlingRepoMock).hent(simulertBehandling.id)
            verify(personServiceMock).hentAktørId(fnr)
            verify(søknadsbehandlingRepoMock).hentEventuellTidligereAttestering(simulertBehandling.id)
            verify(oppgaveServiceMock).opprettOppgave(
                config = OppgaveConfig.Attestering(
                    søknadId = søknadId,
                    aktørId = aktørId,
                    tilordnetRessurs = null
                )
            )
            verify(søknadsbehandlingRepoMock).lagre(expected)
            verify(oppgaveServiceMock).lukkOppgave(oppgaveId)
            verify(eventObserver).handle(argThat { it shouldBe Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingTilAttestering(expected) })
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }
}
