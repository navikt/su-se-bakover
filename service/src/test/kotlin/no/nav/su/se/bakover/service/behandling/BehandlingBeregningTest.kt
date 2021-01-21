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
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.behandling.BehandlingRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandling
import no.nav.su.se.bakover.domain.behandling.Behandling.BehandlingsStatus.BEREGNET_INNVILGET
import no.nav.su.se.bakover.domain.behandling.BehandlingFactory
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.behandlingsinformasjon
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.tidspunkt
import org.junit.jupiter.api.Test
import java.util.UUID

class BehandlingBeregningTest {
    private val sakId = UUID.randomUUID()
    private val saksnummer = Saksnummer(0)
    private val søknadId = UUID.randomUUID()
    private val behandlingId = UUID.randomUUID()
    private val fnr = Fnr("12345678910")
    private val saksbehandler = NavIdentBruker.Saksbehandler("AB12345")
    private val oppgaveId = OppgaveId("o")
    private val journalpostId = JournalpostId("j")
    private val behandlingMetricsMock = mock<BehandlingMetrics>()
    private fun vilkårsvurdertBehandling(): Behandling {

        return BehandlingFactory(behandlingMetricsMock).createBehandling(
            id = behandlingId,
            opprettet = tidspunkt,
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
            status = Behandling.BehandlingsStatus.VILKÅRSVURDERT_INNVILGET,
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            oppgaveId = oppgaveId,
        )
    }

    @Test
    fun `oppretter  beregning`() {
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn vilkårsvurdertBehandling()
        }

        val response = BehandlingTestUtils.createService(
            behandlingRepo = behandlingRepoMock,
            behandlingMetrics = behandlingMetricsMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).opprettBeregning(behandlingId, saksbehandler, Periode.create(1.desember(2020), 31.mars(2021)), emptyList())

        response shouldBe vilkårsvurdertBehandling().copy(
            status = BEREGNET_INNVILGET,
            beregning = response.orNull()!!.beregning(), // Ønsker ikke teste beregning her
        ).right()

        inOrder(behandlingRepoMock) {
            verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe behandlingId })
            verify(behandlingRepoMock).leggTilBeregning(
                behandlingId = argThat { it shouldBe behandlingId },
                beregning = argThat { it shouldBe response.orNull()!!.beregning() }, // Ønsker ikke teste beregning her
            )
            verify(behandlingRepoMock).oppdaterBehandlingStatus(
                behandlingId = argThat { it shouldBe behandlingId },
                status = argThat { it shouldBe BEREGNET_INNVILGET },
            )
        }
        verifyNoMoreInteractions(behandlingRepoMock)
    }

    @Test
    fun `kan ikke hente behandling`() {

        val behandlingRepoMock = mock<BehandlingRepo>()

        val response = BehandlingTestUtils.createService(
            behandlingRepo = behandlingRepoMock,
            behandlingMetrics = behandlingMetricsMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).opprettBeregning(behandlingId, saksbehandler, Periode.create(1.desember(2020), 31.mars(2021)), emptyList())

        response shouldBe KunneIkkeBeregne.FantIkkeBehandling.left()

        verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(behandlingRepoMock)
    }

    @Test
    fun `attestant og saksbehandler kan ikke være like ved opprettelse av beregning`() {
        val behandling = vilkårsvurdertBehandling().copy(
            attestering = Attestering.Iverksatt(NavIdentBruker.Attestant(saksbehandler.navIdent))
        )
        val behandlingRepoMock = mock<BehandlingRepo> {
            on { hentBehandling(any()) } doReturn behandling.copy()
        }

        val response = BehandlingTestUtils.createService(
            behandlingRepo = behandlingRepoMock,
            behandlingMetrics = behandlingMetricsMock,
            microsoftGraphApiOppslag = BehandlingTestUtils.microsoftGraphMock.oppslagMock
        ).opprettBeregning(behandlingId, saksbehandler, Periode.create(1.desember(2020), 31.mars(2021)), emptyList())

        response shouldBe KunneIkkeBeregne.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

        verify(behandlingRepoMock).hentBehandling(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(behandlingRepoMock)
    }
}
