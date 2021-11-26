package no.nav.su.se.bakover.service.klage

import arrow.core.left
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.klage.KunneIkkeVurdereKlage
import no.nav.su.se.bakover.service.argThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.UUID

internal class VurderKlageTest {

    @Test
    fun `fant ikke klage`() {

        val mocks = KlageServiceMocks(
            klageRepoMock = mock {
                on { hentKlage(any()) } doReturn null
            }
        )

        val klageId = UUID.randomUUID()
        val request = KlageVurderingerRequest(
            saksbehandler = NavIdentBruker.Saksbehandler("saksbehandler"),
            klageId = klageId,
            fritekstTilBrev = null,
            omgjør = null,
            oppretthold = null,

        )
        mocks.service.vurder(request) shouldBe KunneIkkeVurdereKlage.FantIkkeKlage.left()
        verify(mocks.klageRepoMock).hentKlage(argThat { it shouldBe klageId })
        mocks.verifyNoMoreInteractions()
    }
}
