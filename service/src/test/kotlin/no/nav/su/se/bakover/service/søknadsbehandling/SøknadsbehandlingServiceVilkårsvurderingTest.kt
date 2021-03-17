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
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withVilkårAvslått
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.behandlingsinformasjon
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
        grunnlagsdata = Grunnlagsdata.EMPTY,
    )

    @Test
    fun `kan ikke hente behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).vilkårsvurder(
            SøknadsbehandlingService.VilkårsvurderRequest(
                behandlingId = behandlingId,
                saksbehandler = saksbehandler,
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
            )
        )

        response shouldBe SøknadsbehandlingService.KunneIkkeVilkårsvurdere.FantIkkeBehandling.left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `vilkårsvurderer med alle vilkår oppfylt`() {
        val behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn opprettetBehandling
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).vilkårsvurder(
            SøknadsbehandlingService.VilkårsvurderRequest(
                behandlingId,
                saksbehandler,
                behandlingsinformasjon
            )
        )

        val expected = Søknadsbehandling.Vilkårsvurdert.Innvilget(
            id = opprettetBehandling.id,
            opprettet = opprettetBehandling.opprettet,
            sakId = opprettetBehandling.sakId,
            saksnummer = opprettetBehandling.saksnummer,
            søknad = opprettetBehandling.søknad,
            oppgaveId = opprettetBehandling.oppgaveId,
            behandlingsinformasjon = behandlingsinformasjon,
            fnr = opprettetBehandling.fnr,
            grunnlagsdata = Grunnlagsdata.EMPTY,
        )

        response shouldBe expected.right()

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
            verify(søknadsbehandlingRepoMock).lagre(expected)
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `vilkårsvurderer med alle avslag`() {
        val behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withVilkårAvslått()
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn opprettetBehandling
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).vilkårsvurder(
            SøknadsbehandlingService.VilkårsvurderRequest(
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
            grunnlagsdata = Grunnlagsdata.EMPTY,
        )

        response shouldBe expected.right()

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
            verify(søknadsbehandlingRepoMock).lagre(expected)
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }
}
