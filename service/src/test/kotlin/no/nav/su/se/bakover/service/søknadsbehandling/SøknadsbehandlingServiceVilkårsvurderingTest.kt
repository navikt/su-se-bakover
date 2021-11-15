package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårAvslått
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderingsresultat
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingUnderkjentInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
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
            it.vilkårsvurderinger.resultat shouldBe Vilkårsvurderingsresultat.Innvilget(
                vilkår = setOf(
                    response.vilkårsvurderinger.uføre,
                    response.vilkårsvurderinger.formue,
                    response.vilkårsvurderinger.flyktning,
                    response.vilkårsvurderinger.lovligOpphold,
                    response.vilkårsvurderinger.fastOpphold,
                    response.vilkårsvurderinger.institusjonsopphold,
                    response.vilkårsvurderinger.oppholdIUtlandet,
                    response.vilkårsvurderinger.personligOppmøte,
                )
            )
        }

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe innvilget.id })
            verify(søknadsbehandlingRepoMock).defaultSessionContext()
            verify(søknadsbehandlingRepoMock).lagre(eq(response), anyOrNull())
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }

    @Test
    fun `vilkårsvurderer med alle avslag`() {
        val uavklart = søknadsbehandlingVilkårsvurdertUavklart(
            grunnlagsdata = Grunnlagsdata.create(bosituasjon = listOf(bosituasjongrunnlagEnslig())),
            vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling(
                uføre = avslåttUførevilkårUtenGrunnlag(),
            ),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårAvslått()
        ).second

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn uavklart
        }

        val response = createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).vilkårsvurder(
            VilkårsvurderRequest(
                uavklart.id,
                uavklart.behandlingsinformasjon,
            ),
        ).getOrFail()

        response shouldBe beOfType<Søknadsbehandling.Vilkårsvurdert.Avslag>()
        response.let {
            it.vilkårsvurderinger.resultat shouldBe Vilkårsvurderingsresultat.Avslag(
                vilkår = setOf(
                    response.vilkårsvurderinger.uføre,
                    response.vilkårsvurderinger.formue,
                    response.vilkårsvurderinger.flyktning,
                    response.vilkårsvurderinger.lovligOpphold,
                    response.vilkårsvurderinger.fastOpphold,
                    response.vilkårsvurderinger.institusjonsopphold,
                    response.vilkårsvurderinger.oppholdIUtlandet,
                    response.vilkårsvurderinger.personligOppmøte,
                )
            )
        }

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe uavklart.id })
            verify(søknadsbehandlingRepoMock).defaultSessionContext()
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

        shouldThrow<StatusovergangVisitor.UgyldigStatusovergangException> {
            createSøknadsbehandlingService(
                søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            ).vilkårsvurder(
                VilkårsvurderRequest(
                    behandlingId = iverksatt.id,
                    behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon()
                        .withAlleVilkårOppfylt(),
                ),
            )
        }.message shouldContain Regex("Ugyldig statusovergang.*Søknadsbehandling.Iverksatt.Innvilget")

        inOrder(søknadsbehandlingRepoMock) {
            verify(søknadsbehandlingRepoMock).hent(argThat { it shouldBe iverksatt.id })
        }
        verifyNoMoreInteractions(søknadsbehandlingRepoMock)
    }
}
