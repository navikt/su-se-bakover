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
import no.nav.su.se.bakover.database.SaksbehandlingRepo
import no.nav.su.se.bakover.database.søknad.SøknadRepo
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withVilkårAvslått
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.behandlingsinformasjon
import no.nav.su.se.bakover.service.behandling.KunneIkkeOppdatereBehandlingsinformasjon
import no.nav.su.se.bakover.service.beregning.BeregningService
import no.nav.su.se.bakover.service.doNothing
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.søknad.SøknadService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadsbehandlingServiceVilkårsvurderingTest {

    private val sakId = UUID.randomUUID()
    private val behandlingId = UUID.randomUUID()
    private val saksbehandler = Saksbehandler("AB12345")
    private val oppgaveId = OppgaveId("o")
    private val opprettetBehandling = Søknadsbehandling.Vilkårsvurdert.Uavklart(
        id = behandlingId,
        opprettet = Tidspunkt.now(),
        behandlingsinformasjon = behandlingsinformasjon,
        søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = oppgaveId,
            journalpostId = JournalpostId("j"),
        ),
        sakId = sakId,
        saksnummer = Saksnummer(0),
        fnr = FnrGenerator.random(),
        oppgaveId = oppgaveId,
    )

    @Test
    fun `kan ikke hente behandling`() {
        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
        ).vilkårsvurder(
            OppdaterSøknadsbehandlingsinformasjonRequest(
                behandlingId = behandlingId,
                saksbehandler = saksbehandler,
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
            )
        )

        response shouldBe KunneIkkeOppdatereBehandlingsinformasjon.FantIkkeBehandling.left()

        verify(behandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(behandlingRepoMock)
    }

    @Test
    fun `vilkårsvurderer med alle vilkår oppfylt`() {
        val behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()
        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn opprettetBehandling
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
        ).vilkårsvurder(
            OppdaterSøknadsbehandlingsinformasjonRequest(
                behandlingId,
                saksbehandler,
                behandlingsinformasjon
            )
        )

        val expected = Søknadsbehandling.Vilkårsvurdert.Innvilget(
            id = opprettetBehandling.id,
            opprettet = opprettetBehandling.opprettet,
            behandlingsinformasjon = behandlingsinformasjon,
            søknad = opprettetBehandling.søknad,
            sakId = opprettetBehandling.sakId,
            saksnummer = opprettetBehandling.saksnummer,
            fnr = opprettetBehandling.fnr,
            oppgaveId = opprettetBehandling.oppgaveId,
        )

        response shouldBe expected.right()

        inOrder(behandlingRepoMock) {
            verify(behandlingRepoMock).hent(argThat { it shouldBe behandlingId })
            verify(behandlingRepoMock).lagre(expected)
        }
        verifyNoMoreInteractions(behandlingRepoMock)
    }

    @Test
    fun `vilkårsvurderer med alle avslag`() {
        val behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withVilkårAvslått()
        val behandlingRepoMock = mock<SaksbehandlingRepo> {
            on { hent(any()) } doReturn opprettetBehandling
        }

        val response = createService(
            behandlingRepo = behandlingRepoMock,
        ).vilkårsvurder(
            OppdaterSøknadsbehandlingsinformasjonRequest(
                behandlingId,
                saksbehandler,
                behandlingsinformasjon
            )
        )

        val expected = Søknadsbehandling.Vilkårsvurdert.Avslag(
            id = opprettetBehandling.id,
            opprettet = opprettetBehandling.opprettet,
            behandlingsinformasjon = behandlingsinformasjon,
            søknad = opprettetBehandling.søknad,
            sakId = opprettetBehandling.sakId,
            saksnummer = opprettetBehandling.saksnummer,
            fnr = opprettetBehandling.fnr,
            oppgaveId = opprettetBehandling.oppgaveId,
        )

        response shouldBe expected.right()

        inOrder(behandlingRepoMock) {
            verify(behandlingRepoMock).hent(argThat { it shouldBe behandlingId })
            verify(behandlingRepoMock).lagre(expected)
        }
        verifyNoMoreInteractions(behandlingRepoMock)
    }

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
}
