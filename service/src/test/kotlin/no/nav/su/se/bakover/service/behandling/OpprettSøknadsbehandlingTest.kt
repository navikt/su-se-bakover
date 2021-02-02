package no.nav.su.se.bakover.service.behandling

import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.NySøknadsbehandling
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.fixedClock
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import org.junit.jupiter.api.Test
import java.util.UUID

internal class OpprettSøknadsbehandlingTest {

    @Test
    fun `Oppretter behandling og publiserer event`() {
        val søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.now(),
            sakId = UUID.randomUUID(),
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            journalpostId = JournalpostId(value = "2"),
            oppgaveId = OppgaveId(value = "1")

        )
        val søknadService: SøknadService = mock {
            on { hentSøknad(any()) } doReturn søknad.right()
        }
        val søknadRepo: SøknadRepo = mock {
            on { harSøknadPåbegyntBehandling(any()) } doReturn false
        }
        val nySøknadCaptor = argumentCaptor<NySøknadsbehandling>()
        val behandlingMock: Behandling = mock()
        val behandlingRepo: BehandlingRepo = mock {
            on { opprettSøknadsbehandling(nySøknadCaptor.capture()) }.doNothing()
            on { hentBehandling(any()) } doReturn behandlingMock
        }
        val behandlingService = BehandlingServiceImpl(
            behandlingRepo = behandlingRepo,
            hendelsesloggRepo = mock(),
            utbetalingService = mock(),
            oppgaveService = mock(),
            søknadService = søknadService,
            søknadRepo = søknadRepo,
            personService = mock(),
            brevService = mock(),
            behandlingMetrics = mock(),
            clock = fixedClock,
            microsoftGraphApiClient = mock(),
            iverksettBehandlingService = mock(),
        )
        val eventObserver: EventObserver = mock {
            on { handle(any()) }.doNothing()
        }
        behandlingService.addObserver(eventObserver)

        behandlingService.opprettSøknadsbehandling(søknad.id) shouldBe behandlingMock.right()
        verify(søknadService).hentSøknad(argThat { it shouldBe søknad.id })
        verify(søknadRepo).harSøknadPåbegyntBehandling(argThat { it shouldBe søknad.id })
        verify(behandlingRepo).opprettSøknadsbehandling(argThat { it shouldBe it })
        verify(behandlingRepo).hentBehandling(argThat { it shouldBe nySøknadCaptor.firstValue.id })
        verify(eventObserver).handle(argThat { it shouldBe Event.Statistikk.BehandlingOpprettet(behandlingMock) })
        nySøknadCaptor.firstValue.let {
            it.sakId shouldBe søknad.sakId
            it.søknadId shouldBe søknad.id
            it.oppgaveId shouldBe søknad.oppgaveId
        }
    }
}
