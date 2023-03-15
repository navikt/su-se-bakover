package no.nav.su.se.bakover.service.skatt

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.skatteetaten.SkatteClient
import no.nav.su.se.bakover.client.skatteetaten.SkatteoppslagFeil
import no.nav.su.se.bakover.domain.skatt.Stadie
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.skatt.skattegrunnlag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock


class SkattServiceImplTest {
    /**
     * error = error
     * true = har denne tilgjengelig
     * false = har ikke tilgjengelig
     */

    private val nettverksfeil = SkatteoppslagFeil.Nettverksfeil(IllegalArgumentException("ok"))

    @Test
    fun `spør for et år - {fastsatt - error, oppgjør - true, utkast - true} - Vi svarer med error`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn listOf(
                    Pair(nettverksfeil.left(), Stadie.FASTSATT),
                    Pair(skattegrunnlag(fnr).right(), Stadie.OPPGJØR),
                    Pair(skattegrunnlag(fnr).right(), Stadie.UTKAST),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe KunneIkkeHenteSkattemelding.KallFeilet(nettverksfeil).left()
    }

    @Test
    fun `spør for et år - {fastsatt - true, oppgjør - true, utkast - true} - Vi svarer med fastsatt`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn listOf(
                    Pair(skattegrunnlag(fnr).right(), Stadie.FASTSATT),
                    Pair(skattegrunnlag(fnr).right(), Stadie.OPPGJØR),
                    Pair(skattegrunnlag(fnr).right(), Stadie.UTKAST),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe skattegrunnlag(fnr).right()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - error, utkast - true} - Vi svarer med error`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn listOf(
                    Pair(SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(), Stadie.FASTSATT),
                    Pair(nettverksfeil.left(), Stadie.OPPGJØR),
                    Pair(skattegrunnlag(fnr).right(), Stadie.UTKAST),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe KunneIkkeHenteSkattemelding.KallFeilet(nettverksfeil).left()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - true, utkast - true} - Vi svarer med oppgjør`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn listOf(
                    Pair(SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(), Stadie.FASTSATT),
                    Pair(skattegrunnlag(fnr).right(), Stadie.OPPGJØR),
                    Pair(skattegrunnlag(fnr).right(), Stadie.UTKAST),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe skattegrunnlag(fnr).right()

    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - false, utkast - error} - Vi svarer med error`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn listOf(
                    Pair(SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(), Stadie.FASTSATT),
                    Pair(SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(), Stadie.OPPGJØR),
                    Pair(nettverksfeil.left(), Stadie.UTKAST),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe KunneIkkeHenteSkattemelding.KallFeilet(nettverksfeil).left()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - false, utkast - true} - Vi svarer med utkast`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn listOf(
                    Pair(SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(), Stadie.FASTSATT),
                    Pair(SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(), Stadie.OPPGJØR),
                    Pair(skattegrunnlag(fnr).right(), Stadie.UTKAST),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe skattegrunnlag(fnr).right()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - false, utkast - false} - Vi svarer med fant ingenting`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn listOf(
                    Pair(SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(), Stadie.FASTSATT),
                    Pair(SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(), Stadie.OPPGJØR),
                    Pair(SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(), Stadie.UTKAST),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe
            KunneIkkeHenteSkattemelding.KallFeilet(SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr).left()

    }

    private fun mockedSkatteClient(
        skatteClient: SkatteClient,
    ) = SkatteServiceImpl(
        skatteClient = skatteClient,
        clock = fixedClock,
    )
}
