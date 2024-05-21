package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppdatereOppgave
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulertSøknadsbehandlingUføre
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import person.domain.PersonService

class SøknadsbehandlingServiceAttesteringTest {

    private val simulertBehandling = simulertSøknadsbehandlingUføre(
        stønadsperiode = Stønadsperiode.create(år(2021)),
    ).second

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
    fun `sender til attestering selv om oppdatering av oppgave feiler`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn simulertBehandling
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { oppdaterOppgave(any(), any()) } doReturn KunneIkkeOppdatereOppgave.FeilVedHentingAvOppgave.left()
        }
        val eventObserver: StatistikkEventObserver = mock()

        val actual = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            oppgaveService = oppgaveServiceMock,
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
        }

        inOrder(søknadsbehandlingRepoMock, oppgaveServiceMock, eventObserver) {
            verify(søknadsbehandlingRepoMock).hent(simulertBehandling.id)
            verify(oppgaveServiceMock).oppdaterOppgave(
                argThat { it shouldBe actual.oppgaveId },
                argThat {
                    it shouldBe OppdaterOppgaveInfo(
                        beskrivelse = "Sendt til attestering",
                        oppgavetype = Oppgavetype.ATTESTERING,
                        tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.IkkeTilordneRessurs,
                    )
                },
            )
            verify(søknadsbehandlingRepoMock).defaultTransactionContext()
            verify(søknadsbehandlingRepoMock).lagre(eq(actual), anyOrNull())
            verify(eventObserver).handle(
                argThat {
                    it shouldBe StatistikkEvent.Behandling.Søknad.TilAttestering.Innvilget(actual as SøknadsbehandlingTilAttestering.Innvilget)
                },
            )
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock, oppgaveServiceMock, eventObserver)
    }
}
