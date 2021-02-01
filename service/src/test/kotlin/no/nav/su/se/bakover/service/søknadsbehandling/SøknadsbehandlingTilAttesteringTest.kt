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
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.database.SaksbehandlingRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.SaksbehandlingServiceImpl
import no.nav.su.se.bakover.service.SendTilAttesteringRequest
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.IverksettSaksbehandlingService
import no.nav.su.se.bakover.service.behandling.KunneIkkeSendeTilAttestering
import no.nav.su.se.bakover.service.beregning.BeregningService
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Test
import java.util.UUID

class SøknadsbehandlingTilAttesteringTest {

    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(0)
    private val søknadId = UUID.randomUUID()
    private val oppgaveId = OppgaveId("o")
    private val fnr = FnrGenerator.random()
    private val nyOppgaveId = OppgaveId("999")
    private val aktørId = AktørId("12345")
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
        oppgaveId = oppgaveId
    )

    private val saksbehandler = NavIdentBruker.Saksbehandler("Z12345")

    @Test
    fun `sjekk at vi sender inn riktig oppgaveId ved lukking av oppgave ved attestering`() {
        val behandlingRepoMock = mock<SaksbehandlingRepo> {
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

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            observer = eventObserver
        ).sendTilAttestering(SendTilAttesteringRequest(simulertBehandling.id, saksbehandler))

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
            saksbehandler = saksbehandler
        )

        actual shouldBe expected.right()

        inOrder(behandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver) {
            verify(behandlingRepoMock).hent(simulertBehandling.id)
            verify(personServiceMock).hentAktørId(fnr)
            verify(behandlingRepoMock).hentEventuellTidligereAttestering(simulertBehandling.id)
            verify(oppgaveServiceMock).opprettOppgave(
                config = OppgaveConfig.Attestering(
                    søknadId = søknadId,
                    aktørId = aktørId,
                    tilordnetRessurs = null
                )
            )
            verify(behandlingRepoMock).lagre(expected)
            verify(oppgaveServiceMock).lukkOppgave(oppgaveId)
            verify(eventObserver).handle(argThat { it shouldBe Event.Statistikk.SøknadsbehandlingTilAttestering(expected) })
        }
        verifyNoMoreInteractions(behandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    @Test
    fun `svarer med feil dersom man ikke finner behandling`() {
        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val personServiceMock: PersonService = mock()
        val oppgaveServiceMock: OppgaveService = mock()
        val eventObserver: EventObserver = mock()

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            observer = eventObserver
        ).sendTilAttestering(SendTilAttesteringRequest(simulertBehandling.id, saksbehandler))

        actual shouldBe KunneIkkeSendeTilAttestering.FantIkkeBehandling.left()

        verify(behandlingRepoMock).hent(simulertBehandling.id)

        verifyNoMoreInteractions(behandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    @Test
    fun `svarer med feil dersom man ikke finner aktørid for person`() {
        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn simulertBehandling
        }

        val personServiceMock: PersonService = mock {
            on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
        }

        val oppgaveServiceMock = mock<OppgaveService>()

        val eventObserver: EventObserver = mock()

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            observer = eventObserver
        ).sendTilAttestering(SendTilAttesteringRequest(simulertBehandling.id, saksbehandler))

        actual shouldBe KunneIkkeSendeTilAttestering.KunneIkkeFinneAktørId.left()

        verify(behandlingRepoMock).hent(simulertBehandling.id)
        verify(personServiceMock).hentAktørId(simulertBehandling.fnr)

        verifyNoMoreInteractions(behandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    @Test
    fun `svarer med feil dersom man ikke får til å opprette oppgave til attestant`() {
        val behandlingRepoMock = mock<SaksbehandlingRepo> {
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

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            observer = eventObserver
        ).sendTilAttestering(SendTilAttesteringRequest(simulertBehandling.id, saksbehandler))

        actual shouldBe KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave.left()

        verify(behandlingRepoMock).hent(simulertBehandling.id)
        verify(personServiceMock).hentAktørId(simulertBehandling.fnr)
        verify(behandlingRepoMock).hentEventuellTidligereAttestering(simulertBehandling.id)
        verify(oppgaveServiceMock).opprettOppgave(
            OppgaveConfig.Attestering(
                søknadId = simulertBehandling.søknad.id,
                aktørId = aktørId,
                tilordnetRessurs = null
            )
        )

        verifyNoMoreInteractions(behandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    @Test
    fun `sender til attestering selv om lukking av eksisterende oppgave feiler`() {
        val behandlingRepoMock = mock<SaksbehandlingRepo> {
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

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            observer = eventObserver
        ).sendTilAttestering(SendTilAttesteringRequest(simulertBehandling.id, saksbehandler))

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
            saksbehandler = saksbehandler
        )

        actual shouldBe expected.right()

        inOrder(behandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver) {
            verify(behandlingRepoMock).hent(simulertBehandling.id)
            verify(personServiceMock).hentAktørId(fnr)
            verify(behandlingRepoMock).hentEventuellTidligereAttestering(simulertBehandling.id)
            verify(oppgaveServiceMock).opprettOppgave(
                config = OppgaveConfig.Attestering(
                    søknadId = søknadId,
                    aktørId = aktørId,
                    tilordnetRessurs = null
                )
            )
            verify(behandlingRepoMock).lagre(expected)
            verify(oppgaveServiceMock).lukkOppgave(oppgaveId)
            verify(eventObserver).handle(argThat { it shouldBe Event.Statistikk.SøknadsbehandlingTilAttestering(expected) })
        }
        verifyNoMoreInteractions(behandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    private fun createService(
        behandlingRepo: SaksbehandlingRepo = mock(),
        utbetalingService: UtbetalingService = mock(),
        oppgaveService: OppgaveService = mock(),
        søknadService: SøknadService = mock(),
        søknadRepo: SøknadRepo = mock(),
        personService: PersonService = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
        iverksettBehandlingService: IverksettSaksbehandlingService = mock(),
        observer: EventObserver = mock { on { handle(any()) }.doNothing() },
        beregningService: BeregningService = mock(),
    ) = SaksbehandlingServiceImpl(
        søknadService,
        søknadRepo,
        behandlingRepo,
        utbetalingService,
        personService,
        oppgaveService,
        iverksettBehandlingService,
        behandlingMetrics,
        beregningService,
    ).apply { addObserver(observer) }
}
