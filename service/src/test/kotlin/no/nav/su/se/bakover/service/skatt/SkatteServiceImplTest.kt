package no.nav.su.se.bakover.service.skatt

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.client.skatteetaten.SkatteClient
import no.nav.su.se.bakover.common.YearRange
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagResponseMedStadie
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagResponseMedYear
import no.nav.su.se.bakover.domain.skatt.SkatteoppslagFeil
import no.nav.su.se.bakover.domain.skatt.Stadie
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import no.nav.su.se.bakover.test.skatt.nyÅrsgrunnlag
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
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagResponseMedYear(
                    skatteResponser = listOf(
                        SamletSkattegrunnlagResponseMedStadie(nettverksfeil.left(), Stadie.FASTSATT),
                        samletOppgjør(),
                        samletUtkast(),
                    ),
                    år = Year.of(2021),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe KunneIkkeHenteSkattemelding.KallFeilet(nettverksfeil).left()
    }

    @Test
    fun `spør for et år - {fastsatt - true, oppgjør - true, utkast - true} - Vi svarer med fastsatt`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagResponseMedYear(
                    listOf(samletFastsatt(), samletOppgjør(), samletUtkast()),
                    Year.of(2021),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe nySkattegrunnlag(fnr).right()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - error, utkast - true} - Vi svarer med error`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagResponseMedYear(
                    listOf(
                        SamletSkattegrunnlagResponseMedStadie(
                            SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                            Stadie.FASTSATT,
                        ),
                        SamletSkattegrunnlagResponseMedStadie(nettverksfeil.left(), Stadie.OPPGJØR),
                        samletUtkast(),
                    ),
                    Year.of(2021),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe KunneIkkeHenteSkattemelding.KallFeilet(nettverksfeil).left()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - true, utkast - true} - Vi svarer med oppgjør`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagResponseMedYear(
                    listOf(
                        SamletSkattegrunnlagResponseMedStadie(
                            SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                            Stadie.FASTSATT,
                        ),
                        samletOppgjør(),
                        samletUtkast(),
                    ),
                    Year.of(2021),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe nySkattegrunnlag(
            fnr,
            årsgrunnlag = nyÅrsgrunnlag(stadie = Stadie.OPPGJØR),
        ).right()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - false, utkast - error} - Vi svarer med error`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagResponseMedYear(
                    listOf(
                        SamletSkattegrunnlagResponseMedStadie(
                            SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                            Stadie.FASTSATT,
                        ),
                        SamletSkattegrunnlagResponseMedStadie(
                            SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                            Stadie.OPPGJØR,
                        ),
                        SamletSkattegrunnlagResponseMedStadie(nettverksfeil.left(), Stadie.UTKAST),
                    ),
                    Year.of(2021),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe KunneIkkeHenteSkattemelding.KallFeilet(nettverksfeil).left()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - false, utkast - true} - Vi svarer med utkast`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagResponseMedYear(
                    listOf(
                        SamletSkattegrunnlagResponseMedStadie(
                            SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                            Stadie.FASTSATT,
                        ),
                        SamletSkattegrunnlagResponseMedStadie(
                            SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                            Stadie.OPPGJØR,
                        ),
                        samletUtkast(),
                    ),
                    Year.of(2021),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe nySkattegrunnlag(
            fnr,
            årsgrunnlag = nyÅrsgrunnlag(stadie = Stadie.UTKAST),
        ).right()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - false, utkast - false} - Vi svarer med fant ingenting`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagResponseMedYear(
                    listOf(
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
                    ),
                    Year.of(2021),
                )
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe
            KunneIkkeHenteSkattemelding.KallFeilet(SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr).left()
    }


    @Test
    fun `ingen skattedata for periode over 3 år`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagResponseMedYear(
                    listOf(
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
                    ),
                    Year.of(2021),
                )
            },
        ).hentSamletSkattegrunnlagForÅr(fnr, YearRange(Year.of(2020), Year.of(2023))) shouldBe
            KunneIkkeHenteSkattemelding.KallFeilet(SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr).left()
    }


    @Test
    fun `henter skattedata for et år (2021)`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlagForÅrsperiode(any(), any()) } doReturn listOf(
                    SamletSkattegrunnlagResponseMedYear(
                        listOf(samletFastsatt(), samletOppgjør(), samletUtkast()),
                        Year.of(2021),
                    ),
                )
            },
        ).hentSamletSkattegrunnlagForÅr(fnr, YearRange(Year.of(2021), Year.of(2021))) shouldBe
            nySkattegrunnlag(fnr).right()
    }


    @Test
    fun `henter skattedata over 3 år (2020-2022)`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlagForÅrsperiode(any(), any()) } doReturn listOf(
                    SamletSkattegrunnlagResponseMedYear(
                        listOf(
                            SamletSkattegrunnlagResponseMedStadie(fastsatt().right(), Stadie.FASTSATT),
                            SamletSkattegrunnlagResponseMedStadie(oppgjør().right(), Stadie.OPPGJØR),
                            SamletSkattegrunnlagResponseMedStadie(utkast().right(), Stadie.UTKAST),
                        ),
                        Year.of(2020),
                    ),
                    SamletSkattegrunnlagResponseMedYear(
                        listOf(
                            SamletSkattegrunnlagResponseMedStadie(fastsatt().right(), Stadie.FASTSATT),
                            SamletSkattegrunnlagResponseMedStadie(oppgjør().right(), Stadie.OPPGJØR),
                            SamletSkattegrunnlagResponseMedStadie(utkast().right(), Stadie.UTKAST),
                        ),
                        Year.of(2021),
                    ),
                    SamletSkattegrunnlagResponseMedYear(
                        listOf(
                            SamletSkattegrunnlagResponseMedStadie(
                                SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                                Stadie.FASTSATT,
                            ),
                            SamletSkattegrunnlagResponseMedStadie(
                                SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr.left(),
                                Stadie.OPPGJØR,
                            ),
                            SamletSkattegrunnlagResponseMedStadie(
                                nyÅrsgrunnlag(inntektsÅr = Year.of(2022), stadie = Stadie.UTKAST).right(),
                                Stadie.UTKAST,
                            ),
                        ),
                        Year.of(2022),
                    ),
                )
            },
        ).hentSamletSkattegrunnlagForÅr(fnr, YearRange(Year.of(2020), Year.of(2022))) shouldBe
            nySkattegrunnlag(årsgrunnlag = nyÅrsgrunnlag(inntektsÅr = Year.of(2022), stadie = Stadie.UTKAST)).right()
    }


    private fun samletFastsatt() = SamletSkattegrunnlagResponseMedStadie(fastsatt().right(), Stadie.FASTSATT)
    private fun samletOppgjør() = SamletSkattegrunnlagResponseMedStadie(oppgjør().right(), Stadie.OPPGJØR)
    private fun samletUtkast() = SamletSkattegrunnlagResponseMedStadie(utkast().right(), Stadie.UTKAST)
    private fun fastsatt() = nyÅrsgrunnlag(stadie = Stadie.FASTSATT)
    private fun oppgjør() = nyÅrsgrunnlag(stadie = Stadie.OPPGJØR)
    private fun utkast() = nyÅrsgrunnlag(stadie = Stadie.UTKAST)

    private fun mockedSkatteClient(
        skatteClient: SkatteClient,
    ): SkatteService = SkatteServiceImpl(
        skatteClient = skatteClient,
        søknadsbehandlingService = mock(),
        clock = fixedClock,
    )
}
