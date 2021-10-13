package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
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
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.attestant
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.behandlingsinformasjon
import no.nav.su.se.bakover.test.generer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doReturnConsecutively
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
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
        søknad = Søknad.Journalført.MedOppgave.IkkeLukket(
            id = UUID.randomUUID(),
            opprettet = Tidspunkt.EPOCH,
            sakId = sakId,
            søknadInnhold = SøknadInnholdTestdataBuilder.build(),
            oppgaveId = oppgaveId,
            journalpostId = JournalpostId("j"),
        ),
        oppgaveId = oppgaveId,
        behandlingsinformasjon = behandlingsinformasjon,
        fnr = Fnr.generer(),
        fritekstTilBrev = "",
        stønadsperiode = stønadsperiode,
        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
        vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        attesteringer = Attesteringshistorikk.empty(),
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
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            attesteringer = Attesteringshistorikk.empty(),
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
            verify(søknadsbehandlingRepoMock).defaultSessionContext()
            verify(søknadsbehandlingRepoMock).lagre(eq(expected), anyOrNull())
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
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            attesteringer = Attesteringshistorikk.empty(),
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
            verify(søknadsbehandlingRepoMock).defaultSessionContext()
            verify(søknadsbehandlingRepoMock).lagre(eq(expected), anyOrNull())
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
            beregning = testBeregning,
            simulering = simulering,
            saksbehandler = saksbehandler,
            attesteringer = Attesteringshistorikk.empty()
                .leggTilNyAttestering(Attestering.Iverksatt(attestant, Tidspunkt.now())),
            fritekstTilBrev = "",
            stønadsperiode = opprettetBehandling.stønadsperiode!!,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
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
