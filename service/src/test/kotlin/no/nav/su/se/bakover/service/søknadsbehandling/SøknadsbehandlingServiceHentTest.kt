package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import org.junit.jupiter.api.Test
import java.util.UUID

internal class SøknadsbehandlingServiceHentTest {
    @Test
    fun `svarer med feil dersom vi ikke finner søknadsbehandling`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }
        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock
        ).hent(HentBehandlingRequest(UUID.randomUUID())).let {
            it shouldBe FantIkkeBehandling.left()
        }
    }

    @Test
    fun `svarer med behandling dersom alt er ok`() {
        val behandlingMock = mock<Søknadsbehandling>()
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandlingMock
        }
        createSøknadsbehandlingService(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock
        ).hent(HentBehandlingRequest(UUID.randomUUID())).let {
            it shouldBe behandlingMock.right()
        }
    }
}
