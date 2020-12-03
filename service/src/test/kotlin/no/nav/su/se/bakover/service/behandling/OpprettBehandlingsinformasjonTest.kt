package no.nav.su.se.bakover.service.behandling

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
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker.Attestant
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.VILKÅRSVURDERT_INNVILGET
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.behandlingsinformasjon
import org.junit.jupiter.api.Test
import java.util.UUID

internal class OpprettBehandlingsinformasjonTest {

    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(0)
    private val søknadId = UUID.randomUUID()
    private val behandlingId = UUID.randomUUID()
    private val fnr = Fnr("12345678910")
    private val saksbehandler = Saksbehandler("AB12345")
    private val oppgaveId = OppgaveId("o")
    private val journalpostId = JournalpostId("j")
    private val behandlingMetricsMock = mock<BehandlingMetrics>()
    private fun opprettetBehandling(): Behandling {
        return BehandlingFactory(behandlingMetricsMock).createBehandling(
            id = behandlingId,
            opprettet = BehandlingTestUtils.tidspunkt,
            behandlingsinformasjon = behandlingsinformasjon,
            søknad = Søknad.Journalført.MedOppgave(
                id = søknadId,
                opprettet = Tidspunkt.EPOCH,
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                oppgaveId = oppgaveId,
                journalpostId = journalpostId,
            ),
            beregning = null,
            simulering = null,
            status = Behandling.BehandlingsStatus.OPPRETTET,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            oppgaveId = oppgaveId,
        )
    }

    @Test
    fun `kan ikke hente behandling`() {
        val behandlingInformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn null
        }

        val response = BehandlingTestUtils.createService(
            behandlingRepo = behandlingRepoMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).oppdaterBehandlingsinformasjon(behandlingId, saksbehandler, behandlingInformasjon)

        response shouldBe KunneIkkeOppdatereBehandlingsinformasjon.FantIkkeBehandling.left()

        verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(behandlingRepoMock)
    }

    @Test
    fun `attestant som har underkjent og saksbehandler kan ikke være den samme`() {
        val behandlingInformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()

        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn opprettetBehandling().copy(attestering = Attestering.Iverksatt(Attestant(saksbehandler.navIdent)))
        }

        val response = BehandlingTestUtils.createService(
            behandlingRepo = behandlingRepoMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).oppdaterBehandlingsinformasjon(behandlingId, saksbehandler, behandlingInformasjon)

        response shouldBe KunneIkkeOppdatereBehandlingsinformasjon.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(behandlingRepoMock)
    }

    @Test
    fun `happy case`() {
        val behandlingInformasjon = behandlingsinformasjon
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn opprettetBehandling()
        }

        val response = BehandlingTestUtils.createService(
            behandlingRepo = behandlingRepoMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).oppdaterBehandlingsinformasjon(behandlingId, saksbehandler, behandlingInformasjon)

        response shouldBe opprettetBehandling().copy(status = VILKÅRSVURDERT_INNVILGET).right()

        inOrder(behandlingRepoMock) {
            verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe behandlingId })
            verify(behandlingRepoMock).slettBeregning(
                argThat { it shouldBe behandlingId }
            )
            verify(behandlingRepoMock).oppdaterBehandlingsinformasjon(
                argThat { it shouldBe behandlingId },
                argThat { it shouldBe behandlingInformasjon }
            )
            verify(behandlingRepoMock).oppdaterBehandlingStatus(
                argThat { it shouldBe behandlingId },
                argThat { it shouldBe VILKÅRSVURDERT_INNVILGET }
            )
        }
        verifyNoMoreInteractions(behandlingRepoMock)
    }
}
