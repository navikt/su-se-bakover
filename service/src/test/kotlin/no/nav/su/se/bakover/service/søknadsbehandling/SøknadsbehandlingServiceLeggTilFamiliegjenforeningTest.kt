package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.service.vilkår.FamiliegjenforeningvilkårStatus
import no.nav.su.se.bakover.service.vilkår.LeggTilFamiliegjenforegningRequest
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingIkkeVurdertAlder
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.util.UUID

internal class SøknadsbehandlingServiceLeggTilFamiliegjenforeningTest {

    @Test
    fun `fant ikke behandling`() {
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn null
            },
        ).let {
            it.søknadsbehandlingService.leggTilFamiliegjenforeningvilkår(
                request = LeggTilFamiliegjenforegningRequest(
                    behandlingId = UUID.randomUUID(),
                    status = FamiliegjenforeningvilkårStatus.Uavklart,
                ),
            ) shouldBe SøknadsbehandlingService.KunneIkkeLeggeTilFamiliegjenforeningVilkårService.FantIkkeBehandling.left()
        }
    }

    @Test
    fun `får lagt til familiegjenforening vilkår`() {
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn søknadsbehandlingVilkårsvurdertUavklart(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdertAlder()).second
            },
        ).let { søknadsbehandlingServiceAndMocks ->
            val behandlingId = UUID.randomUUID()
            val actual = søknadsbehandlingServiceAndMocks.søknadsbehandlingService.leggTilFamiliegjenforeningvilkår(
                request = LeggTilFamiliegjenforegningRequest(
                    behandlingId = behandlingId,
                    status = FamiliegjenforeningvilkårStatus.VilkårOppfylt,
                ),
            ).getOrFail()

            actual.let {
                it.vilkårsvurderinger.familiegjenforening().shouldBeRight().let {
                    it.resultat shouldBe Resultat.Innvilget
                }
            }

            verify(søknadsbehandlingServiceAndMocks.søknadsbehandlingRepo).hent(behandlingId)
        }
    }
}
