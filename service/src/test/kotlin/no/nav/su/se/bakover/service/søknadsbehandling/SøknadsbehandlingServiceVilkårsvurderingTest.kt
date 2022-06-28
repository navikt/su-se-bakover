package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertAvslag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class SøknadsbehandlingServiceVilkårsvurderingTest {

    @Test
    fun `kan ikke hente behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).vilkårsvurder(
            VilkårsvurderRequest(
                behandlingId = UUID.randomUUID(),
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon(),
            ),
        )

        response shouldBe SøknadsbehandlingService.KunneIkkeVilkårsvurdere.FantIkkeBehandling.left()

        verify(søknadsbehandlingRepoMock).hent(any())
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `vilkårsvurderer med alle vilkår oppfylt`() {
        val innvilget = søknadsbehandlingUnderkjentInnvilget().second

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn innvilget
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).vilkårsvurder(
            VilkårsvurderRequest(
                behandlingId = innvilget.id,
                behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            ),
        ).getOrFail()

        response shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Innvilget>()
        response.let {
            it.vilkårsvurderinger.vurdering shouldBe Vilkårsvurderingsresultat.Innvilget(
                vilkår = setOf(
                    response.vilkårsvurderinger.uføreVilkår().getOrFail(),
                    response.vilkårsvurderinger.formue,
                    response.vilkårsvurderinger.flyktningVilkår().getOrFail(),
                    response.vilkårsvurderinger.lovligOpphold,
                    response.vilkårsvurderinger.fastOpphold,
                    response.vilkårsvurderinger.institusjonsopphold,
                    response.vilkårsvurderinger.utenlandsopphold,
                    response.vilkårsvurderinger.personligOppmøte,
                    response.vilkårsvurderinger.opplysningsplikt,
                ),
            )
        }

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilget.id })
            verify(søknadsbehandlingRepoMock).defaultTransactionContext()
            verify(søknadsbehandlingRepoMock).lagre(eq(response), anyOrNull())
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `vilkårsvurderer med alle avslag`() {
        val avslag = søknadsbehandlingVilkårsvurdertAvslag().second

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn avslag
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).vilkårsvurder(
            VilkårsvurderRequest(
                avslag.id,
                avslag.behandlingsinformasjon,
            ),
        ).getOrFail()

        response shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
        response.let {
            it.vilkårsvurderinger.vurdering shouldBe Vilkårsvurderingsresultat.Avslag(
                vilkår = setOf(
                    response.vilkårsvurderinger.uføreVilkår().getOrFail(),
                    response.vilkårsvurderinger.formue,
                    response.vilkårsvurderinger.flyktningVilkår().getOrFail(),
                    response.vilkårsvurderinger.lovligOpphold,
                    response.vilkårsvurderinger.fastOpphold,
                    response.vilkårsvurderinger.institusjonsopphold,
                    response.vilkårsvurderinger.utenlandsopphold,
                    response.vilkårsvurderinger.personligOppmøte,
                    response.vilkårsvurderinger.opplysningsplikt,
                ),
            )
        }

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe avslag.id })
            verify(søknadsbehandlingRepoMock).defaultTransactionContext()
            verify(søknadsbehandlingRepoMock).lagre(eq(response), anyOrNull())
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `kan ikke vilkårsvurdere en iverksatt behandling`() {
        val iverksatt = søknadsbehandlingIverksattInnvilget().second

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn iverksatt
        }

        shouldThrow<IllegalStateException> {
            createSøknadsbehandlingService(
                søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            ).vilkårsvurder(
                VilkårsvurderRequest(
                    behandlingId = iverksatt.id,
                    behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
                        .withAlleVilkårOppfylt(),
                ),
            )
        }

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe iverksatt.id })
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }
}
