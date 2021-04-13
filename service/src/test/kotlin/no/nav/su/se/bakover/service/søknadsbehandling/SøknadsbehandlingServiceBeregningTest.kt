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
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.Behandlingsperiode
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.tidspunkt
import no.nav.su.se.bakover.service.beregning.BeregningService
import no.nav.su.se.bakover.service.beregning.TestBeregning
import org.junit.jupiter.api.Test
import java.util.UUID

class SøknadsbehandlingServiceBeregningTest {
    private val sakId = UUID.randomUUID()
    private val behandlingId = UUID.randomUUID()
    private val behandlingsperiode = Behandlingsperiode(Periode.create(1.januar(2021), 31.desember(2021)))
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
        fritekstTilBrev = "",
        behandlingsperiode = behandlingsperiode,
    )

    @Test
    fun `oppretter beregning`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn vilkårsvurdertBehandling
        }
        val beregningServiceMock = mock<BeregningService> {
            on { beregn(any(), any()) } doReturn TestBeregning
        }

        val request = SøknadsbehandlingService.BeregnRequest(
            behandlingId = behandlingId,
            fradrag = emptyList(),
        )

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            beregningService = beregningServiceMock,
        ).beregn(
            request,
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
            beregning = TestBeregning,
            fritekstTilBrev = "",
            behandlingsperiode = vilkårsvurdertBehandling.behandlingsperiode,
        )

        response shouldBe expected.right()

        inOrder(søknadsbehandlingRepoMock, beregningServiceMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
            verify(beregningServiceMock).beregn(
                søknadsbehandling = argThat { it shouldBe vilkårsvurdertBehandling },
                fradrag = argThat { it shouldBe request.fradrag },
            )
            verify(søknadsbehandlingRepoMock).lagre(expected)
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `kan ikke hente behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo>()

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).beregn(
            SøknadsbehandlingService.BeregnRequest(
                behandlingId = behandlingId,
                fradrag = emptyList(),
            ),
        )

        response shouldBe SøknadsbehandlingService.KunneIkkeBeregne.FantIkkeBehandling.left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }
}
