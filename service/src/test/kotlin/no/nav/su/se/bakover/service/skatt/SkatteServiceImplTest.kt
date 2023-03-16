package no.nav.su.se.bakover.service.skatt

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.skatteetaten.SamletSkattegrunnlagResponseMedStadie
import no.nav.su.se.bakover.client.skatteetaten.SkatteClient
import no.nav.su.se.bakover.client.skatteetaten.SkatteoppslagFeil
import no.nav.su.se.bakover.common.YearRange
import no.nav.su.se.bakover.domain.skatt.Stadie
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Year

class SkatteServiceImplTest {
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
                    SamletSkattegrunnlagResponseMedStadie(nettverksfeil.left(), Stadie.FASTSATT),
                    samletOppgjør(), samletUtkast(),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe KunneIkkeHenteSkattemelding.KallFeilet(nettverksfeil).left()
    }

    @Test
    fun `spør for et år - {fastsatt - true, oppgjør - true, utkast - true} - Vi svarer med fastsatt`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn
                    listOf(samletFastsatt(), samletOppgjør(), samletUtkast())
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe nySkattegrunnlag(fnr).right()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - error, utkast - true} - Vi svarer med error`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn listOf(
                    SamletSkattegrunnlagResponseMedStadie(
                        SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                        Stadie.FASTSATT,
                    ),
                    SamletSkattegrunnlagResponseMedStadie(nettverksfeil.left(), Stadie.OPPGJØR),
                    samletUtkast(),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe KunneIkkeHenteSkattemelding.KallFeilet(nettverksfeil).left()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - true, utkast - true} - Vi svarer med oppgjør`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn listOf(
                    SamletSkattegrunnlagResponseMedStadie(
                        SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                        Stadie.FASTSATT,
                    ),
                    samletOppgjør(), samletUtkast(),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe nySkattegrunnlag(fnr, Stadie.OPPGJØR).right()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - false, utkast - error} - Vi svarer med error`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn listOf(
                    SamletSkattegrunnlagResponseMedStadie(
                        SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                        Stadie.FASTSATT,
                    ),
                    SamletSkattegrunnlagResponseMedStadie(
                        SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                        Stadie.OPPGJØR,
                    ),
                    SamletSkattegrunnlagResponseMedStadie(nettverksfeil.left(), Stadie.UTKAST),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe KunneIkkeHenteSkattemelding.KallFeilet(nettverksfeil).left()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - false, utkast - true} - Vi svarer med utkast`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn listOf(
                    SamletSkattegrunnlagResponseMedStadie(
                        SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                        Stadie.FASTSATT,
                    ),
                    SamletSkattegrunnlagResponseMedStadie(
                        SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                        Stadie.OPPGJØR,
                    ),
                    samletUtkast(),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe nySkattegrunnlag(fnr, Stadie.UTKAST).right()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - false, utkast - false} - Vi svarer med fant ingenting`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn listOf(
                    SamletSkattegrunnlagResponseMedStadie(
                        SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                        Stadie.FASTSATT,
                    ),
                    SamletSkattegrunnlagResponseMedStadie(
                        SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                        Stadie.OPPGJØR,
                    ),
                    SamletSkattegrunnlagResponseMedStadie(
                        SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                        Stadie.UTKAST,
                    ),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe
            KunneIkkeHenteSkattemelding.KallFeilet(SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr).left()
    }


    @Test
    fun `ingen skattedata for periode over 3 år`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn listOf(
                    SamletSkattegrunnlagResponseMedStadie(
                        SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                        Stadie.FASTSATT,
                    ),
                    SamletSkattegrunnlagResponseMedStadie(
                        SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                        Stadie.OPPGJØR,
                    ),
                    SamletSkattegrunnlagResponseMedStadie(
                        SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                        Stadie.UTKAST,
                    ),
                )
            },
        ).hentSamletSkattegrunnlagForÅr(fnr, YearRange(Year.of(2020), Year.of(2023))) shouldBe
            KunneIkkeHenteSkattemelding.KallFeilet(SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr).left()
    }

    @Test
    fun `henter skattedata for år 2021`(){
        mockedSkatteClient(
            mock {
                on {hentSamletSkattegrunnlag(any(), any())} doReturn listOf(samletFastsatt())
            }
        ).hentSamletSkattegrunnlagForÅr(fnr, YearRange(Year.of(2021), Year.of(2021))) shouldBe
            fastsatt().right()
    }


    private fun samletFastsatt() = SamletSkattegrunnlagResponseMedStadie(fastsatt().right(), Stadie.FASTSATT)
    private fun samletOppgjør() = SamletSkattegrunnlagResponseMedStadie(oppgjør().right(), Stadie.OPPGJØR)
    private fun samletUtkast() = SamletSkattegrunnlagResponseMedStadie(utkast().right(), Stadie.UTKAST)
    private fun fastsatt() = nySkattegrunnlag(fnr, Stadie.FASTSATT)
    private fun oppgjør() = nySkattegrunnlag(fnr, Stadie.OPPGJØR)
    private fun utkast() = nySkattegrunnlag(fnr, Stadie.UTKAST)

    private fun mockedSkatteClient(
        skatteClient: SkatteClient,
    ): SkatteService = SkatteServiceImpl(
        skatteClient = skatteClient,
        clock = fixedClock,
    )
}
