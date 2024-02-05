package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.VilkårsvurdertSøknadsbehandling
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

internal class SøknadsbehandlingServiceHentTest {
    @Test
    fun `svarer med feil dersom vi ikke finner søknadsbehandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }
        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).hent(SøknadsbehandlingService.HentRequest(SøknadsbehandlingId.generer())).let {
            it shouldBe SøknadsbehandlingService.FantIkkeBehandling.left()
        }
    }

    @Test
    fun `svarer med behandling dersom alt er ok`() {
        val behandlingMock = mock<VilkårsvurdertSøknadsbehandling.Uavklart>()
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandlingMock
        }
        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        ).hent(SøknadsbehandlingService.HentRequest(SøknadsbehandlingId.generer())).let {
            it shouldBe behandlingMock.right()
        }
    }
}
