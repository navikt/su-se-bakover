package no.nav.su.se.bakover.service.skatt

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.YearRange
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagResponseMedStadie
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagResponseMedYear
import no.nav.su.se.bakover.domain.skatt.Skatteoppslag
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

    private val nettverksfeil =
        SkatteoppslagFeil.Nettverksfeil(IllegalArgumentException("Her skjedde det en nettverskfeil"))

    @Test
    fun `spør for et år - {fastsatt - error, oppgjør - true, utkast - true} - Vi svarer med error`() {
        mockedSkatteClient(
            mock {
                val år = Year.of(2021)
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagResponseMedYear(
                    skatteResponser = listOf(
                        SamletSkattegrunnlagResponseMedStadie(nettverksfeil.left(), Stadie.FASTSATT, år),
                        samletOppgjør(år),
                        samletUtkast(år),
                    ),
                    år = år,
                ).right()
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe KunneIkkeHenteSkattemelding.KallFeilet(nettverksfeil).left()
    }

    @Test
    fun `spør for et år - {fastsatt - true, oppgjør - true, utkast - true} - Vi svarer med fastsatt`() {
        val år = Year.of(2021)
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagResponseMedYear(
                    listOf(samletFastsatt(år), samletOppgjør(år), samletUtkast(år)),
                    år,
                ).right()
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe nySkattegrunnlag(fnr).right()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - error, utkast - true} - Vi svarer med error`() {
        mockedSkatteClient(
            mock {
                val år = Year.of(2021)
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagResponseMedYear(
                    listOf(
                        SamletSkattegrunnlagResponseMedStadie(
                            SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år).left(),
                            Stadie.FASTSATT,
                            inntektsår = år,
                        ),
                        SamletSkattegrunnlagResponseMedStadie(nettverksfeil.left(), Stadie.OPPGJØR, år),
                        samletUtkast(år),
                    ),
                    år,
                ).right()
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe KunneIkkeHenteSkattemelding.KallFeilet(nettverksfeil).left()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - true, utkast - true} - Vi svarer med oppgjør`() {
        mockedSkatteClient(
            mock {
                val år = Year.of(2021)
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagResponseMedYear(
                    listOf(
                        SamletSkattegrunnlagResponseMedStadie(
                            SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år).left(),
                            Stadie.FASTSATT,
                            inntektsår = år,
                        ),
                        samletOppgjør(år),
                        samletUtkast(år),
                    ),
                    år,
                ).right()
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
                val år = Year.of(2021)
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagResponseMedYear(
                    listOf(
                        SamletSkattegrunnlagResponseMedStadie(
                            SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år).left(),
                            Stadie.FASTSATT,
                            inntektsår = år,
                        ),
                        SamletSkattegrunnlagResponseMedStadie(
                            SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år).left(),
                            Stadie.OPPGJØR,
                            inntektsår = år,
                        ),
                        SamletSkattegrunnlagResponseMedStadie(nettverksfeil.left(), Stadie.UTKAST, år),
                    ),
                    år,
                ).right()
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe KunneIkkeHenteSkattemelding.KallFeilet(nettverksfeil).left()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - false, utkast - true} - Vi svarer med utkast`() {
        mockedSkatteClient(
            mock {
                val år = Year.of(2021)
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagResponseMedYear(
                    listOf(
                        SamletSkattegrunnlagResponseMedStadie(
                            SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år).left(),
                            Stadie.FASTSATT,
                            inntektsår = år,
                        ),
                        SamletSkattegrunnlagResponseMedStadie(
                            SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år).left(),
                            Stadie.OPPGJØR,
                            inntektsår = år,
                        ),
                        samletUtkast(år),
                    ),
                    år,
                ).right()
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe nySkattegrunnlag(
            fnr,
            årsgrunnlag = nyÅrsgrunnlag(stadie = Stadie.UTKAST),
        ).right()
    }

    @Test
    fun `spør for et år - {fastsatt - false, oppgjør - false, utkast - false} - Vi svarer med fant ingenting`() {
        val år = Year.of(2021)
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagResponseMedYear(
                    listOf(
                        SamletSkattegrunnlagResponseMedStadie(
                            SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år).left(),
                            Stadie.FASTSATT,
                            inntektsår = år,
                        ),
                        SamletSkattegrunnlagResponseMedStadie(
                            SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år).left(),
                            Stadie.OPPGJØR,
                            inntektsår = år,
                        ),
                        SamletSkattegrunnlagResponseMedStadie(
                            SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år).left(),
                            Stadie.UTKAST,
                            inntektsår = år,
                        ),
                    ),
                    år,
                ).right()
            },
        ).hentSamletSkattegrunnlag(fnr) shouldBe KunneIkkeHenteSkattemelding.KallFeilet(
            SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(
                år,
            ),
        ).left()
    }

    @Test
    fun `ingen skattedata for periode over 3 år`() {
        val år = Year.of(2021)
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlagForÅrsperiode(any(), any()) } doReturn listOf(
                    SamletSkattegrunnlagResponseMedYear(
                        listOf(
                            SamletSkattegrunnlagResponseMedStadie(
                                SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år).left(),
                                Stadie.FASTSATT,
                                inntektsår = år,
                            ),
                            SamletSkattegrunnlagResponseMedStadie(
                                SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år).left(),
                                Stadie.OPPGJØR,
                                inntektsår = år,
                            ),
                            SamletSkattegrunnlagResponseMedStadie(
                                SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år).left(),
                                Stadie.UTKAST,
                                inntektsår = år,
                            ),
                        ),
                        år,
                    ),
                ).right()
            },
        ).hentSamletSkattegrunnlagForÅr(
            fnr,
            YearRange(Year.of(2020), Year.of(2023)),
        ) shouldBe KunneIkkeHenteSkattemelding.KallFeilet(SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(år))
            .left()
    }

    @Test
    fun `henter skattedata for et år (2021)`() {
        val år = Year.of(2021)
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlagForÅrsperiode(any(), any()) } doReturn listOf(
                    SamletSkattegrunnlagResponseMedYear(
                        listOf(samletFastsatt(år), samletOppgjør(år), samletUtkast(år)),
                        år,
                    ),
                ).right()
            },
        ).hentSamletSkattegrunnlagForÅr(fnr, YearRange(år, år)) shouldBe nySkattegrunnlag(fnr).right()
    }

    @Test
    fun `henter skattedata over 3 år (2020-2022)`() {
        mockedSkatteClient(
            mock {
                on { hentSamletSkattegrunnlagForÅrsperiode(any(), any()) } doReturn listOf(
                    SamletSkattegrunnlagResponseMedYear(
                        listOf(
                            SamletSkattegrunnlagResponseMedStadie(fastsatt().right(), Stadie.FASTSATT, Year.of(2020)),
                            SamletSkattegrunnlagResponseMedStadie(oppgjør().right(), Stadie.OPPGJØR, Year.of(2020)),
                            SamletSkattegrunnlagResponseMedStadie(utkast().right(), Stadie.UTKAST, Year.of(2020)),
                        ),
                        Year.of(2020),
                    ),
                    SamletSkattegrunnlagResponseMedYear(
                        listOf(
                            SamletSkattegrunnlagResponseMedStadie(fastsatt().right(), Stadie.FASTSATT, Year.of(2021)),
                            SamletSkattegrunnlagResponseMedStadie(oppgjør().right(), Stadie.OPPGJØR, Year.of(2021)),
                            SamletSkattegrunnlagResponseMedStadie(utkast().right(), Stadie.UTKAST, Year.of(2021)),
                        ),
                        Year.of(2021),
                    ),
                    SamletSkattegrunnlagResponseMedYear(
                        listOf(
                            SamletSkattegrunnlagResponseMedStadie(
                                SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(Year.of(2022)).left(),
                                Stadie.FASTSATT,
                                Year.of(2022),
                            ),
                            SamletSkattegrunnlagResponseMedStadie(
                                SkatteoppslagFeil.FantIkkeSkattegrunnlagForPersonOgÅr(Year.of(2022)).left(),
                                Stadie.OPPGJØR,
                                Year.of(2022),
                            ),
                            SamletSkattegrunnlagResponseMedStadie(
                                nyÅrsgrunnlag(inntektsÅr = Year.of(2022), stadie = Stadie.UTKAST).right(),
                                Stadie.UTKAST,
                                Year.of(2022),
                            ),
                        ),
                        Year.of(2022),
                    ),
                ).right()
            },
        ).hentSamletSkattegrunnlagForÅr(fnr, YearRange(Year.of(2020), Year.of(2022))) shouldBe nySkattegrunnlag(
            årsgrunnlag = nyÅrsgrunnlag(inntektsÅr = Year.of(2022), stadie = Stadie.UTKAST),
        ).right()
    }

    private fun samletFastsatt(år: Year) =
        SamletSkattegrunnlagResponseMedStadie(fastsatt().right(), Stadie.FASTSATT, år)

    private fun samletOppgjør(år: Year) = SamletSkattegrunnlagResponseMedStadie(oppgjør().right(), Stadie.OPPGJØR, år)
    private fun samletUtkast(år: Year) = SamletSkattegrunnlagResponseMedStadie(utkast().right(), Stadie.UTKAST, år)
    private fun fastsatt() = nyÅrsgrunnlag(stadie = Stadie.FASTSATT)
    private fun oppgjør() = nyÅrsgrunnlag(stadie = Stadie.OPPGJØR)
    private fun utkast() = nyÅrsgrunnlag(stadie = Stadie.UTKAST)

    private fun mockedSkatteClient(
        skatteClient: Skatteoppslag,
    ): SkatteService = SkatteServiceImpl(
        skatteClient = skatteClient,
        søknadsbehandlingService = mock(),
        clock = fixedClock,
    )
}
