package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.person.AktørId
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeOppretteOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttestering.KunneIkkeSendeSøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulertSøknadsbehandlingUføre
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.KunneIkkeHentePerson
import person.domain.PersonService

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

        actual.shouldBeType<SøknadsbehandlingTilAttestering.Innvilget>().also {
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
                    it shouldBe StatistikkEvent.Behandling.Søknad.TilAttestering.Innvilget(actual as SøknadsbehandlingTilAttestering.Innvilget)
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

        shouldThrow<IllegalArgumentException> {
            createSøknadsbehandlingService(
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
        }.message shouldBe "Søknadsbehandling send til attestering: Fant ikke søknadsbehandling med id ${simulertBehandling.id}. Avbryter handlingen."

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

        actual shouldBe KunneIkkeSendeSøknadsbehandlingTilAttestering.KunneIkkeFinneAktørId.left()

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

        actual shouldBe KunneIkkeSendeSøknadsbehandlingTilAttestering.KunneIkkeOppretteOppgave.left()

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
            on { lukkOppgave(any()) } doAnswer { KunneIkkeLukkeOppgave.FeilVedHentingAvOppgave(it.getArgument(0)).left() }
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

        actual.shouldBeType<SøknadsbehandlingTilAttestering.Innvilget>().also {
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
                    it shouldBe StatistikkEvent.Behandling.Søknad.TilAttestering.Innvilget(actual as SøknadsbehandlingTilAttestering.Innvilget)
                },
            )
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver)
    }
}
