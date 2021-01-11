package no.nav.su.se.bakover.service.behandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.AktørId
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.createService
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import org.junit.jupiter.api.Test
import java.util.UUID

class BehandlingTilAttesteringTest {
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(0)
    private val søknadId = UUID.randomUUID()
    private val fnr = FnrGenerator.random()
    private val oppgaveId = OppgaveId("o")
    private val journalpostId = JournalpostId("j")
    private val nyOppgaveId = OppgaveId("999")
    private val aktørId = AktørId("12345")

    private val beregning = TestBeregning

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "NAVN",
        datoBeregnet = idag(),
        nettoBeløp = 191500,
        periodeList = listOf()
    )

    private val simulertBehandling = BehandlingFactory(mock()).createBehandling(
        søknad = Søknad.Journalført.MedOppgave(
            id = søknadId,
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = oppgaveId,
            journalpostId = journalpostId
        ),
        beregning = beregning,
        simulering = simulering,
        status = Behandling.BehandlingsStatus.SIMULERT,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        oppgaveId = oppgaveId
    )

    private val saksbehandler = NavIdentBruker.Saksbehandler("Z12345")

    @Test
    fun `sjekk at vi sender inn riktig oppgaveId ved lukking av oppgave ved attestering`() {
        val behandling = simulertBehandling.copy()

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
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
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock,
            observer = eventObserver
        ).sendTilAttestering(behandling.id, saksbehandler)

        actual shouldBe simulertBehandling.copy(
            saksbehandler = saksbehandler,
            status = Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET,
            oppgaveId = nyOppgaveId
        ).right()

        inOrder(behandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver) {
            verify(behandlingRepoMock).hentBehandling(simulertBehandling.id)
            verify(personServiceMock).hentAktørId(fnr)
            verify(oppgaveServiceMock).opprettOppgave(
                config = OppgaveConfig.Attestering(
                    søknadId = søknadId,
                    aktørId = aktørId,
                    tilordnetRessurs = null
                )
            )
            verify(behandlingRepoMock).oppdaterOppgaveId(
                argThat { it shouldBe simulertBehandling.id },
                argThat { it shouldBe nyOppgaveId }
            )

            verify(behandlingRepoMock).settSaksbehandler(simulertBehandling.id, saksbehandler)
            verify(behandlingRepoMock).oppdaterBehandlingStatus(
                simulertBehandling.id,
                Behandling.BehandlingsStatus.TIL_ATTESTERING_INNVILGET
            )

            verify(oppgaveServiceMock).lukkOppgave(oppgaveId)
            verify(eventObserver).handle(argThat { it shouldBe Event.Statistikk.BehandlingTilAttestering(behandling) })
        }
        verifyNoMoreInteractions(behandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    @Test
    fun `attestant kan ikke saksbehandle underkjent behandling`() {
        val behandling =
            simulertBehandling.copy(attestering = Attestering.Iverksatt(NavIdentBruker.Attestant(saksbehandler.navIdent)))

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling
        }

        val personServiceMock: PersonService = mock()

        val oppgaveServiceMock = mock<OppgaveService>()

        val eventObserver: EventObserver = mock {
            on { handle(any()) }.doNothing()
        }

        val actual = createService(
            behandlingRepo = behandlingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).sendTilAttestering(behandling.id, saksbehandler)

        actual shouldBe KunneIkkeSendeTilAttestering.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        verify(behandlingRepoMock).hentBehandling(simulertBehandling.id)
        verifyNoMoreInteractions(behandlingRepoMock, personServiceMock, oppgaveServiceMock)
        verifyZeroInteractions(eventObserver)
    }
}
