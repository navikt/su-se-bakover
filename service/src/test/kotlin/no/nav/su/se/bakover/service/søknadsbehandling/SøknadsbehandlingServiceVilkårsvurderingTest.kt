package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.doReturnConsecutively
import com.nhaarman.mockitokotlin2.inOrder
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.behandling.withVilkårAvslått
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.FnrGenerator
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.attestant
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.behandlingsinformasjon
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadsbehandlingServiceVilkårsvurderingTest {

    private val sakId = UUID.randomUUID()
    private val behandlingId = UUID.randomUUID()
    private val saksbehandler = Saksbehandler("AB12345")
    private val oppgaveId = OppgaveId("o")
    private val stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021)))
    private val opprettetBehandling = Søknadsbehandling.Vilkårsvurdert.Uavklart(
        id = behandlingId,
        opprettet = Tidspunkt.now(),
        sakId = sakId,
        saksnummer = Saksnummer(2021),
        søknad = Søknad.Journalført.MedOppgave(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = oppgaveId,
            journalpostId = JournalpostId("j"),
        ),
        oppgaveId = oppgaveId,
        behandlingsinformasjon = behandlingsinformasjon,
        fnr = FnrGenerator.random(),
        fritekstTilBrev = "",
        stønadsperiode = stønadsperiode,
        vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
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
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            ),
        )

        response shouldBe SøknadsbehandlingService.KunneIkkeVilkårsvurdere.FantIkkeBehandling.left()

        verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `vilkårsvurderer med alle vilkår oppfylt`() {
        val behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt()

        val expected = Søknadsbehandling.Vilkårsvurdert.Innvilget(
            id = opprettetBehandling.id,
            opprettet = opprettetBehandling.opprettet,
            sakId = opprettetBehandling.sakId,
            saksnummer = opprettetBehandling.saksnummer,
            søknad = opprettetBehandling.søknad,
            oppgaveId = opprettetBehandling.oppgaveId,
            behandlingsinformasjon = behandlingsinformasjon,
            fnr = opprettetBehandling.fnr,
            fritekstTilBrev = "",
            stønadsperiode = opprettetBehandling.stønadsperiode!!,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturnConsecutively listOf(opprettetBehandling, expected)
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).vilkårsvurder(
            SøknadsbehandlingService.VilkårsvurderRequest(
                behandlingId,
                behandlingsinformasjon,
            ),
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

        val expected = Søknadsbehandling.Vilkårsvurdert.Avslag(
            id = opprettetBehandling.id,
            opprettet = opprettetBehandling.opprettet,
            sakId = opprettetBehandling.sakId,
            saksnummer = opprettetBehandling.saksnummer,
            søknad = opprettetBehandling.søknad,
            oppgaveId = opprettetBehandling.oppgaveId,
            behandlingsinformasjon = behandlingsinformasjon,
            fnr = opprettetBehandling.fnr,
            fritekstTilBrev = "",
            stønadsperiode = opprettetBehandling.stønadsperiode!!,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturnConsecutively listOf(opprettetBehandling, expected)
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).vilkårsvurder(
            SøknadsbehandlingService.VilkårsvurderRequest(
                behandlingId,
                behandlingsinformasjon,
            ),
        )

        response shouldBe expected.right()

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
            verify(søknadsbehandlingRepoMock).lagre(expected)
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `kan ikke vilkårsvurdere en iverksatt behandling`() {
        val behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withVilkårAvslått()

        val iverksattInnvilgetBehandling = Søknadsbehandling.Iverksatt.Innvilget(
            id = opprettetBehandling.id,
            opprettet = opprettetBehandling.opprettet,
            sakId = opprettetBehandling.sakId,
            saksnummer = opprettetBehandling.saksnummer,
            søknad = opprettetBehandling.søknad,
            oppgaveId = opprettetBehandling.oppgaveId,
            behandlingsinformasjon = opprettetBehandling.behandlingsinformasjon,
            fnr = opprettetBehandling.fnr,
            beregning = beregning,
            simulering = simulering,
            saksbehandler = saksbehandler,
            attestering = Attestering.Iverksatt(attestant),
            fritekstTilBrev = "",
            stønadsperiode = opprettetBehandling.stønadsperiode!!,
            grunnlagsdata = Grunnlagsdata.EMPTY,
            vilkårsvurderinger = Vilkårsvurderinger.EMPTY,
        )

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn iverksattInnvilgetBehandling
        }

        shouldThrow<StatusovergangVisitor.UgyldigStatusovergangException> {
            createSøknadsbehandlingService(
                søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            ).vilkårsvurder(
                SøknadsbehandlingService.VilkårsvurderRequest(
                    iverksattInnvilgetBehandling.id,
                    behandlingsinformasjon,
                ),
            )
        }.message shouldContain Regex("Ugyldig statusovergang.*Søknadsbehandling.Iverksatt.Innvilget")

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe behandlingId })
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }
}
