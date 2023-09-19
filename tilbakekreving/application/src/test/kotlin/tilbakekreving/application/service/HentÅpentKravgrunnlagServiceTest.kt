package tilbakekreving.application.service

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.nyKravgrunnlag
import no.nav.su.se.bakover.test.nyRåttKravgrunnlag
import no.nav.su.se.bakover.test.sakId
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import tilbakekreving.domain.kravgrunnlag.KravgrunnlagRepo

class HentÅpentKravgrunnlagServiceTest {

    @Test
    fun `henter aktive kravgrunnlag`() {
        val kravgrunnlagRepo = mock<KravgrunnlagRepo> {
            on { hentÅpentKravgrunnlagForSak(any()) } doReturn nyRåttKravgrunnlag()
        }

        val tilgangstyringService = mock<TilbakekrevingsbehandlingTilgangstyringService> {
            on { assertHarTilgangTilSak(any()) } doReturn Unit.right()
        }

        val mocks = mockedServices(kravgrunnlagRepo, tilgangstyringService)
        mocks.service().hentÅpentKravgrunnlag(
            sakId = sakId,
            kravgrunnlagMapper = { nyKravgrunnlag().right() },
        ).shouldBeRight()

        verify(kravgrunnlagRepo).hentÅpentKravgrunnlagForSak(argThat { it shouldBe sakId })
        mocks.verifyNoMoreInteractions()
    }

    private data class mockedServices(
        private val kravgrunnlagRepo: KravgrunnlagRepo = mock(),
        private val tilgangstyringService: TilbakekrevingsbehandlingTilgangstyringService = mock(),
    ) {
        fun service(): HentÅpentKravgrunnlagService =
            HentÅpentKravgrunnlagService(kravgrunnlagRepo, tilgangstyringService)

        fun verifyNoMoreInteractions() {
            verify(tilgangstyringService).assertHarTilgangTilSak(any())
            org.mockito.kotlin.verifyNoMoreInteractions(kravgrunnlagRepo, tilgangstyringService)
        }
    }
}
