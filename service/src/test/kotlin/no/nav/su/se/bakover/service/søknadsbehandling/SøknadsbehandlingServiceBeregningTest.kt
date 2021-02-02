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
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.SaksbehandlingRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.tidspunkt
import no.nav.su.se.bakover.service.behandling.KunneIkkeBeregne
import no.nav.su.se.bakover.service.beregning.BeregningService
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Test
import java.util.UUID

class SøknadsbehandlingServiceBeregningTest {
    private val sakId = UUID.randomUUID()
    private val behandlingId = UUID.randomUUID()
    private val vilkårsvurdertBehandling = Søknadsbehandling.Vilkårsvurdert.Innvilget(
        id = UUID.randomUUID(),
        opprettet = tidspunkt,
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
        søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = OppgaveId("o"),
            journalpostId = JournalpostId("j"),
        ),
        sakId = sakId,
        saksnummer = Saksnummer(0),
        fnr = FnrGenerator.random(),
        oppgaveId = OppgaveId("o"),
    )

    private fun createService(
        behandlingRepo: SaksbehandlingRepo = mock(),
        utbetalingService: UtbetalingService = mock(),
        oppgaveService: OppgaveService = mock(),
        søknadService: SøknadService = mock(),
        søknadRepo: SøknadRepo = mock(),
        personService: PersonService = mock(),
        behandlingMetrics: BehandlingMetrics = mock(),
        iverksettBehandlingService: IverksettSøknadsbehandlingService = mock(),
        observer: EventObserver = mock { on { handle(any()) }.doNothing() },
        beregningService: BeregningService = mock(),
    ) = SøknadsbehandlingServiceImpl(
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

    @Test
    fun `oppretter beregning`() {
        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn vilkårsvurdertBehandling
        }
        val beregningServiceMock = mock<BeregningService> {
            on { beregn(any(), any(), any()) } doReturn TestBeregning
        }

        val request = OpprettBeregningRequest(
            behandlingId = behandlingId,
            periode = Periode.create(1.desember(2021), 31.mars(2022)),
            fradrag = emptyList()
        )

        val response = createService(
            behandlingRepo = behandlingRepoMock,
            beregningService = beregningServiceMock
        ).beregn(
            request
        )

        val expected = Søknadsbehandling.Beregnet.Innvilget(
            id = vilkårsvurdertBehandling.id,
            opprettet = vilkårsvurdertBehandling.opprettet,
            behandlingsinformasjon = vilkårsvurdertBehandling.behandlingsinformasjon,
            søknad = vilkårsvurdertBehandling.søknad,
            sakId = vilkårsvurdertBehandling.sakId,
            saksnummer = vilkårsvurdertBehandling.saksnummer,
            fnr = vilkårsvurdertBehandling.fnr,
            oppgaveId = vilkårsvurdertBehandling.oppgaveId,
            beregning = TestBeregning
        )

        response shouldBe expected.right()

        inOrder(behandlingRepoMock, beregningServiceMock) {
            verify(behandlingRepoMock).hent(argThat { it shouldBe behandlingId })
            verify(beregningServiceMock).beregn(
                søknadsbehandling = argThat { it shouldBe vilkårsvurdertBehandling },
                periode = argThat { it shouldBe request.periode },
                fradrag = argThat { it shouldBe request.fradrag },
            )
            verify(behandlingRepoMock).lagre(expected)
        }
        verifyNoMoreInteractions(behandlingRepoMock)
    }

    @Test
    fun `kan ikke hente behandling`() {
        val behandlingRepoMock = mock<SaksbehandlingRepo>()

        val response = createService(
            behandlingRepo = behandlingRepoMock,
        ).beregn(
            OpprettBeregningRequest(
                behandlingId = behandlingId,
                periode = Periode.create(1.desember(2021), 31.mars(2022)),
                fradrag = emptyList()
            )
        )

        response shouldBe KunneIkkeBeregne.FantIkkeBehandling.left()

        verify(behandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(behandlingRepoMock)
    }
}
