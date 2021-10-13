package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.su.se.bakover.common.juni
import no.nav.su.se.bakover.common.mars
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils.behandlingId
import no.nav.su.se.bakover.service.sak.FantIkkeSak
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.UUID

internal class SøknadsbehandlingServiceOppdaterStønadsperiodeTest {

    @Test
    fun `svarer med feil hvis man ikke finner behandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).let {
            val response = it.søknadsbehandlingService.oppdaterStønadsperiode(
                SøknadsbehandlingService.OppdaterStønadsperiodeRequest(
                    behandlingId = behandlingId,
                    stønadsperiode = stønadsperiode2021,
                ),
            )
            response shouldBe SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.FantIkkeBehandling.left()

            verify(it.søknadsbehandlingRepo).hent(behandlingId)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil hvis man ikke finner sak`() {
        val (sak, behandling) = søknadsbehandlingVilkårsvurdertUavklart()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn FantIkkeSak.left()
        }

        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            sakService = sakServiceMock,
        ).let {
            val response = it.søknadsbehandlingService.oppdaterStønadsperiode(
                SøknadsbehandlingService.OppdaterStønadsperiodeRequest(
                    behandlingId = behandling.id,
                    stønadsperiode = stønadsperiode2021,
                ),
            )
            response shouldBe SøknadsbehandlingService.KunneIkkeOppdatereStønadsperiode.FantIkkeSak.left()

            verify(it.søknadsbehandlingRepo).hent(behandling.id)
            verify(it.sakService).hentSak(sak.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `kaster exception ved hvs søknadsbehandling er i ugyldig tilstand for oppdatering av stønadsperiode`() {
        val (sak, tilAttestering) = søknadsbehandlingTilAttesteringAvslagUtenBeregning()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn tilAttestering
        }

        val sakServiceMock = mock<SakService>() {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
            SøknadsbehandlingServiceAndMocks(
                søknadsbehandlingRepo = søknadsbehandlingRepoMock,
                sakService = sakServiceMock,
            ).søknadsbehandlingService.oppdaterStønadsperiode(
                SøknadsbehandlingService.OppdaterStønadsperiodeRequest(behandlingId, stønadsperiode2021),
            )
        }
    }

    @Test
    fun `oppdaterer stønadsperiode for behandling, grunnlagsdata og vilkårsvurderinger`() {
        val (sak, uavklart) = søknadsbehandlingVilkårsvurdertInnvilget()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn uavklart
            on { hentForSak(any(), anyOrNull()) } doReturn emptyList()
        }

        val sakServiceMock = mock<SakService>() {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val nyStønadsperiode = Stønadsperiode.create(
            periode = Periode.create(1.juni(2021), 31.mars(2022)),
            begrunnelse = "begg",
        )

        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            sakService = sakServiceMock,
        ).let { it ->
            val response = it.søknadsbehandlingService.oppdaterStønadsperiode(
                SøknadsbehandlingService.OppdaterStønadsperiodeRequest(
                    behandlingId = uavklart.id,
                    stønadsperiode = nyStønadsperiode,
                ),
            ).getOrFail("feil i testdataoppsett")

            uavklart.stønadsperiode.periode shouldNotBe nyStønadsperiode

            verify(it.søknadsbehandlingRepo).hent(uavklart.id)
            verify(it.sakService).hentSak(sak.id)
            verify(it.søknadsbehandlingRepo).defaultSessionContext()
            verify(it.søknadsbehandlingRepo).lagre(
                argThat {
                    it shouldBe response
                    it.stønadsperiode shouldBe nyStønadsperiode
                },
                anyOrNull(),
            )
            verify(it.vilkårsvurderingService).lagre(
                argThat { it shouldBe uavklart.id },
                argThat { vilkårsvurderinger ->
                    vilkårsvurderinger.uføre.grunnlag.all { it.periode == nyStønadsperiode.periode } shouldBe true
                    vilkårsvurderinger.formue.grunnlag.all { it.periode == nyStønadsperiode.periode } shouldBe true
                },
            )
            verify(it.grunnlagService).lagreBosituasjongrunnlag(
                argThat { it shouldBe uavklart.id },
                argThat { bosituasjon ->
                    bosituasjon.all { it.periode == nyStønadsperiode.periode } shouldBe true
                },
            )
            verify(it.grunnlagService).lagreFradragsgrunnlag(
                argThat { it shouldBe uavklart.id },
                argThat { fradrag ->
                    fradrag.all { it.periode == nyStønadsperiode.periode } shouldBe true
                },
            )
            it.verifyNoMoreInteractions()
        }
    }
}
