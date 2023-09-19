package no.nav.su.se.bakover.tilbakekreving.application.service

import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.TilbakekrevingRepo
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.nyKravgrunnlag
import no.nav.su.se.bakover.test.nyRåttKravgrunnlag
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.tilbakekreving.domain.ManuellTilbakekrevingService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Clock

class ManuellTilbakekrevingServiceImplTest {

    @Test
    fun `henter aktive kravgrunnlag`() {
        val tilbakekrevingRepo = mock<TilbakekrevingRepo> {
            on { hentSisteFerdigbehandledeKravgrunnlagForSak(any()) } doReturn nyRåttKravgrunnlag()
        }

        val mocks = mockedServices(tilbakekrevingRepo)
        mocks.manuellTilbakekrevingService().hentAktivKravgrunnlag(
            sakId = sakId,
            kravgrunnlagMapper = { nyKravgrunnlag().right() },
        ).shouldBeRight()

        verify(tilbakekrevingRepo).hentSisteFerdigbehandledeKravgrunnlagForSak(argThat { it shouldBe sakId })
        mocks.verifyNoMoreInteractions()
    }

    @Test
    fun `oppretter ny manuell tilbakekrevingsbehandling`() {
        val tilbakekrevingRepo = mock<TilbakekrevingRepo> {
            on { hentSisteFerdigbehandledeKravgrunnlagForSak(any()) } doReturn nyRåttKravgrunnlag()
        }
        val mocks = mockedServices(tilbakekrevingRepo)
        mocks.manuellTilbakekrevingService().ny(
            sakId = sakId,
            kravgrunnlagMapper = { nyKravgrunnlag().right() },
        ).shouldBeRight()

        verify(tilbakekrevingRepo).hentSisteFerdigbehandledeKravgrunnlagForSak(argThat { it shouldBe sakId })
        mocks.verifyNoMoreInteractions()
    }

    private data class mockedServices(
        private val tilbakekrevingRepo: TilbakekrevingRepo = mock(),
        private val clock: Clock = fixedClock,
    ) {
        fun manuellTilbakekrevingService(): ManuellTilbakekrevingService =
            ManuellTilbakekrevingServiceImpl(tilbakekrevingRepo, clock)

        fun verifyNoMoreInteractions() {
            org.mockito.kotlin.verifyNoMoreInteractions(tilbakekrevingRepo)
        }
    }
}
