package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.vilkår.Resultat
import no.nav.su.se.bakover.service.vilkår.FamiliegjenforeningVurderinger
import no.nav.su.se.bakover.service.vilkår.FamiliegjenforeningvilkårStatus
import no.nav.su.se.bakover.service.vilkår.LeggTilFamiliegjenforeningRequest
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.stønadsperiode2022
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.vilkårsvurderingSøknadsbehandlingIkkeVurdertAlder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
                request = LeggTilFamiliegjenforeningRequest(
                    behandlingId = UUID.randomUUID(),
                    vurderinger = listOf(
                        FamiliegjenforeningVurderinger(
                            stønadsperiode2021.periode,
                            FamiliegjenforeningvilkårStatus.Uavklart,
                        ),
                    ),
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
                request = LeggTilFamiliegjenforeningRequest(
                    behandlingId = behandlingId,
                    vurderinger = listOf(
                        FamiliegjenforeningVurderinger(
                            stønadsperiode2021.periode,
                            FamiliegjenforeningvilkårStatus.VilkårOppfylt,
                        ),
                    ),
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

    @Test
    fun `kaster hvis vurderinger ikke har noen elementer`() {
        assertThrows<IndexOutOfBoundsException> {
            SøknadsbehandlingServiceAndMocks(
                søknadsbehandlingRepo = mock {
                    on { hent(any()) } doReturn søknadsbehandlingVilkårsvurdertUavklart(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdertAlder()).second
                },
            ).let { søknadsbehandlingServiceAndMocks ->
                val behandlingId = UUID.randomUUID()
                søknadsbehandlingServiceAndMocks.søknadsbehandlingService.leggTilFamiliegjenforeningvilkår(
                    request = LeggTilFamiliegjenforeningRequest(
                        behandlingId = behandlingId,
                        vurderinger = emptyList(),
                    ),
                )
            }
        }
    }

    @Test
    fun `kaster hvis vurderinger mer enn 1 element`() {
        assertThrows<IllegalArgumentException> {
            SøknadsbehandlingServiceAndMocks(
                søknadsbehandlingRepo = mock {
                    on { hent(any()) } doReturn søknadsbehandlingVilkårsvurdertUavklart(vilkårsvurderinger = vilkårsvurderingSøknadsbehandlingIkkeVurdertAlder()).second
                },
            ).let { søknadsbehandlingServiceAndMocks ->
                val behandlingId = UUID.randomUUID()
                søknadsbehandlingServiceAndMocks.søknadsbehandlingService.leggTilFamiliegjenforeningvilkår(
                    request = LeggTilFamiliegjenforeningRequest(
                        behandlingId = behandlingId,
                        vurderinger = listOf(
                            FamiliegjenforeningVurderinger(
                                stønadsperiode2021.periode,
                                FamiliegjenforeningvilkårStatus.VilkårOppfylt,
                            ),
                            FamiliegjenforeningVurderinger(
                                stønadsperiode2022.periode,
                                FamiliegjenforeningvilkårStatus.VilkårOppfylt,
                            ),
                        ),
                    ),
                )
            }
        }
    }
}
