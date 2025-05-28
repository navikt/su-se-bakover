package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.sak.Sakstype
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.FamiliegjenforeningVurderinger
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.FamiliegjenforeningvilkårStatus
import no.nav.su.se.bakover.domain.vilkår.familiegjenforening.LeggTilFamiliegjenforeningRequest
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import vilkår.common.domain.Vurdering
import java.time.YearMonth

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
                    behandlingId = SøknadsbehandlingId.generer(),
                    vurderinger = listOf(
                        FamiliegjenforeningVurderinger(Periode(YearMonth.now()), FamiliegjenforeningvilkårStatus.Uavklart),
                    ),
                ),
                saksbehandler = saksbehandler,
            ) shouldBe SøknadsbehandlingService.KunneIkkeLeggeTilFamiliegjenforeningVilkårService.FantIkkeBehandling.left()
        }
    }

    @Test
    fun `får lagt til familiegjenforening vilkår`() {
        val søknadsbehandling = nySøknadsbehandlingMedStønadsperiode(
            sakstype = Sakstype.ALDER,
        ).second
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn søknadsbehandling
            },
        ).let { søknadsbehandlingServiceAndMocks ->
            val behandlingId = SøknadsbehandlingId.generer()
            val actual = søknadsbehandlingServiceAndMocks.søknadsbehandlingService.leggTilFamiliegjenforeningvilkår(
                request = LeggTilFamiliegjenforeningRequest(
                    behandlingId = behandlingId,
                    vurderinger = listOf(
                        FamiliegjenforeningVurderinger(søknadsbehandling.periode, FamiliegjenforeningvilkårStatus.VilkårOppfylt),
                    ),
                ),
                saksbehandler = saksbehandler,
            ).getOrFail()

            actual.let {
                it.vilkårsvurderinger.familiegjenforening().shouldBeRight().let {
                    it.vurdering shouldBe Vurdering.Innvilget
                }
            }

            verify(søknadsbehandlingServiceAndMocks.søknadsbehandlingRepo).hent(behandlingId)
        }
    }

    @Test
    fun `kaster hvis vurderinger ikke har noen elementer`() {
        val vilkårsvurdertSøknadsbehandling = nySøknadsbehandlingMedStønadsperiode().second
        assertThrows<IllegalArgumentException>("") {
            SøknadsbehandlingServiceAndMocks(
                søknadsbehandlingRepo = mock {
                    on { hent(any()) } doReturn vilkårsvurdertSøknadsbehandling
                },
            ).søknadsbehandlingService.leggTilFamiliegjenforeningvilkår(
                request = LeggTilFamiliegjenforeningRequest(
                    behandlingId = SøknadsbehandlingId.generer(),
                    vurderinger = emptyList(),
                ),
                saksbehandler = saksbehandler,
            )
        }
    }
}
