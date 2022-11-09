package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.AktørId
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.AvventerKravgrunnlag
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.person.PersonService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulertSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.søknadsbehandlingSimulert
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

class SøknadsbehandlingServiceAttesteringTest {

    private val nyOppgaveId = OppgaveId("999")
    private val aktørId = AktørId("12345")
    private val simulertBehandling = simulertSøknadsbehandlingUføre(
        stønadsperiode = Stønadsperiode.create(år(2021)),
    ).second

    @Test
    fun `sjekk at vi sender inn riktig oppgaveId ved lukking av oppgave ved attestering`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn simulertBehandling
        }

        val personServiceMock: PersonService = mock {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn nyOppgaveId.right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val eventObserver: StatistikkEventObserver = mock()

        val gammelOppgaveId = simulertBehandling.oppgaveId

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            observer = eventObserver,
        ).sendTilAttestering(
            SøknadsbehandlingService.SendTilAttesteringRequest(
                simulertBehandling.id,
                saksbehandler,
                "",
            ),
        ).getOrFail()

        actual.shouldBeType<Søknadsbehandling.TilAttestering.Innvilget>().also {
            it.oppgaveId shouldBe nyOppgaveId
            it.saksbehandler shouldBe saksbehandler
            it.fritekstTilBrev shouldBe ""
        }

        inOrder(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver) {
            verify(søknadsbehandlingRepoMock).hent(simulertBehandling.id)
            verify(personServiceMock).hentAktørId(simulertBehandling.fnr)
            verify(oppgaveServiceMock).opprettOppgave(
                config = OppgaveConfig.AttesterSøknadsbehandling(
                    søknadId = simulertBehandling.søknad.id,
                    aktørId = aktørId,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                ),
            )
            verify(søknadsbehandlingRepoMock).defaultTransactionContext()
            verify(søknadsbehandlingRepoMock).lagre(eq(actual), anyOrNull())
            verify(oppgaveServiceMock).lukkOppgave(gammelOppgaveId)
            verify(eventObserver).handle(
                argThat {
                    it shouldBe StatistikkEvent.Behandling.Søknad.TilAttestering.Innvilget(actual as Søknadsbehandling.TilAttestering.Innvilget)
                },
            )
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
        val eventObserver: StatistikkEventObserver = mock()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            observer = eventObserver,
        ).sendTilAttestering(
            SøknadsbehandlingService.SendTilAttesteringRequest(
                simulertBehandling.id,
                saksbehandler,
                "",
            ),
        )

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

        val eventObserver: StatistikkEventObserver = mock()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            observer = eventObserver,
        ).sendTilAttestering(
            SøknadsbehandlingService.SendTilAttesteringRequest(
                simulertBehandling.id,
                saksbehandler,
                "",
            ),
        )

        actual shouldBe SøknadsbehandlingService.KunneIkkeSendeTilAttestering.KunneIkkeFinneAktørId.left()

        verify(søknadsbehandlingRepoMock).hent(simulertBehandling.id)
        verify(personServiceMock).hentAktørId(simulertBehandling.fnr)

        verifyNoMoreInteractions(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    @Test
    fun `svarer med feil dersom man ikke får til å opprette oppgave til attestant`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn simulertBehandling
        }

        val personServiceMock: PersonService = mock {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }
        val oppgaveServiceMock: OppgaveService = mock {
            on { opprettOppgave(any()) } doReturn KunneIkkeOppretteOppgave.left()
        }
        val eventObserver: StatistikkEventObserver = mock()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            observer = eventObserver,
        ).sendTilAttestering(
            SøknadsbehandlingService.SendTilAttesteringRequest(
                simulertBehandling.id,
                saksbehandler,
                "",
            ),
        )

        actual shouldBe SøknadsbehandlingService.KunneIkkeSendeTilAttestering.KunneIkkeOppretteOppgave.left()

        verify(søknadsbehandlingRepoMock).hent(simulertBehandling.id)
        verify(personServiceMock).hentAktørId(simulertBehandling.fnr)
        verify(oppgaveServiceMock).opprettOppgave(
            argThat {
                it shouldBe OppgaveConfig.AttesterSøknadsbehandling(
                    søknadId = simulertBehandling.søknad.id,
                    aktørId = aktørId,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                )
            },
        )

        verifyNoMoreInteractions(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    @Test
    fun `sender til attestering selv om lukking av eksisterende oppgave feiler`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn simulertBehandling
        }

        val personServiceMock: PersonService = mock {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }

        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn nyOppgaveId.right()
            on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
        }

        val eventObserver: StatistikkEventObserver = mock()

        val gammelOppgaveId = simulertBehandling.oppgaveId

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
            personService = personServiceMock,
            observer = eventObserver,
        ).sendTilAttestering(
            SøknadsbehandlingService.SendTilAttesteringRequest(
                behandlingId = simulertBehandling.id,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "",
            ),
        ).getOrFail()

        actual.shouldBeType<Søknadsbehandling.TilAttestering.Innvilget>().also {
            it.saksbehandler shouldBe saksbehandler
            it.oppgaveId shouldBe nyOppgaveId
        }

        inOrder(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver) {
            verify(søknadsbehandlingRepoMock).hent(simulertBehandling.id)
            verify(personServiceMock).hentAktørId(simulertBehandling.fnr)
            verify(oppgaveServiceMock).opprettOppgave(
                config = OppgaveConfig.AttesterSøknadsbehandling(
                    søknadId = simulertBehandling.søknad.id,
                    aktørId = aktørId,
                    tilordnetRessurs = null,
                    clock = fixedClock,
                ),
            )
            verify(søknadsbehandlingRepoMock).defaultTransactionContext()
            verify(søknadsbehandlingRepoMock).lagre(eq(actual), anyOrNull())
            verify(oppgaveServiceMock).lukkOppgave(gammelOppgaveId)
            verify(eventObserver).handle(
                argThat {
                    it shouldBe StatistikkEvent.Behandling.Søknad.TilAttestering.Innvilget(actual as Søknadsbehandling.TilAttestering.Innvilget)
                },
            )
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }

    @Test
    fun `får ikke sendt til attestering dersom det eksisterer revurderinger som avventer kravgrunnlag`() {
        val søknadsbehandling = søknadsbehandlingSimulert().second
        val mock = mock<AvventerKravgrunnlag>()

        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn søknadsbehandling
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn listOf(mock)
            },
        ).let {
            it.søknadsbehandlingService.sendTilAttestering(
                SøknadsbehandlingService.SendTilAttesteringRequest(
                    behandlingId = søknadsbehandling.id,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "nei",
                ),
            ) shouldBe SøknadsbehandlingService.KunneIkkeSendeTilAttestering.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving.left()
        }
    }
}
