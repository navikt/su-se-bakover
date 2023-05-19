package no.nav.su.se.bakover.service.skatt

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.YearRange
import no.nav.su.se.bakover.common.tid.toRange
import no.nav.su.se.bakover.domain.skatt.KunneIkkeHenteSkattemelding
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagForÅr
import no.nav.su.se.bakover.domain.skatt.SamletSkattegrunnlagForÅrOgStadie
import no.nav.su.se.bakover.domain.skatt.Skatteoppslag
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlag
import no.nav.su.se.bakover.test.skatt.nySkattegrunnlagForÅr
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.time.Clock
import java.time.Year

class SkatteServiceImplTest {
    /**
     * error = error
     * true = har denne tilgjengelig
     * false = har ikke tilgjengelig
     */

    @Test
    fun `spør for et år - {oppgjør - error, utkast - true} - Vi svarer med error`() {
        val mocked = mockedServices(
            mock {
                val år = Year.of(2020)
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagForÅr(
                    utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = år,
                    ),
                    oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = KunneIkkeHenteSkattemelding.Nettverksfeil.left(),
                        inntektsår = år,
                    ),
                    år = år,
                )
            },
        )

        mocked.service.hentSamletSkattegrunnlag(fnr, saksbehandler).let {
            it shouldBe nySkattegrunnlag(
                id = it.id,
                fnr = fnr,
                saksbehandler = saksbehandler,
                årsgrunnlag = nonEmptyListOf(
                    SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = KunneIkkeHenteSkattemelding.Nettverksfeil.left(),
                        inntektsår = Year.of(2020),
                    ),
                ),
            )
        }
    }

    @Test
    fun `spør for et år - {oppgjør - true, utkast - true} - Vi svarer med oppgjør`() {
        val mocked = mockedServices(
            mock {
                val år = Year.of(2020)
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagForÅr(
                    utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = år,
                    ),
                    oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = år,
                    ),
                    år = år,
                )
            },
        )

        mocked.service.hentSamletSkattegrunnlag(fnr, saksbehandler).let {
            it shouldBe nySkattegrunnlag(
                id = it.id,
                fnr = fnr,
                saksbehandler = saksbehandler,
                årsgrunnlag = nonEmptyListOf(
                    SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = Year.of(2020),
                    ),
                ),
                clock = fixedClock,
                hentetTidspunkt = fixedTidspunkt,
                årSpurtFor = Year.of(2020).toRange(),
            )
        }
    }

    @Test
    fun `spør for et år - {oppgjør - false, utkast - error} - Vi svarer med error`() {
        val mocked = mockedServices(
            mock {
                val år = Year.of(2020)
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagForÅr(
                    utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = KunneIkkeHenteSkattemelding.Nettverksfeil.left(),
                        inntektsår = år,
                    ),
                    oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                        inntektsår = år,
                    ),
                    år = år,
                )
            },
        )

        mocked.service.hentSamletSkattegrunnlag(fnr, saksbehandler).let {
            it shouldBe nySkattegrunnlag(
                id = it.id,
                fnr = fnr,
                saksbehandler = saksbehandler,
                årsgrunnlag = nonEmptyListOf(
                    SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = KunneIkkeHenteSkattemelding.Nettverksfeil.left(),
                        inntektsår = Year.of(2020),
                    ),
                ),
                clock = fixedClock,
                hentetTidspunkt = fixedTidspunkt,
                årSpurtFor = Year.of(2020).toRange(),
            )
        }
    }

    @Test
    fun `spør for et år - {oppgjør - false, utkast - true} - Vi svarer med utkast`() {
        val mocked = mockedServices(
            mock {
                val år = Year.of(2020)
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagForÅr(
                    utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = år,
                    ),
                    oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                        inntektsår = år,
                    ),
                    år = år,
                )
            },
        )

        mocked.service.hentSamletSkattegrunnlag(fnr, saksbehandler).let {
            it shouldBe nySkattegrunnlag(
                id = it.id,
                fnr = fnr,
                saksbehandler = saksbehandler,
                årsgrunnlag = nonEmptyListOf(
                    SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = Year.of(2020),
                    ),
                ),
                clock = fixedClock,
                hentetTidspunkt = fixedTidspunkt,
                årSpurtFor = Year.of(2020).toRange(),
            )
        }
    }

    @Test
    fun `spør for et år - {oppgjør - false, utkast - false} - Vi svarer med fant ingenting`() {
        val år = Year.of(2020)
        val mocked = mockedServices(
            mock {
                on { hentSamletSkattegrunnlag(any(), any()) } doReturn SamletSkattegrunnlagForÅr(
                    utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                        inntektsår = år,
                    ),
                    oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                        inntektsår = år,
                    ),
                    år = år,
                )
            },
        )

        mocked.service.hentSamletSkattegrunnlag(fnr, saksbehandler).let {
            it shouldBe nySkattegrunnlag(
                id = it.id,
                fnr = fnr,
                saksbehandler = saksbehandler,
                årsgrunnlag = nonEmptyListOf(
                    SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                        inntektsår = Year.of(2020),
                    ),
                ),
                clock = fixedClock,
                hentetTidspunkt = fixedTidspunkt,
                årSpurtFor = Year.of(2020).toRange(),
            )
        }
    }

    @Test
    fun `ingen skattedata for periode over 3 år`() {
        val mocked = mockedServices(
            mock {
                on { hentSamletSkattegrunnlagForÅrsperiode(any(), any()) } doReturn nonEmptyListOf(
                    SamletSkattegrunnlagForÅr(
                        utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                            oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                            inntektsår = Year.of(2021),
                        ),
                        oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                            oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                            inntektsår = Year.of(2021),
                        ),
                        år = Year.of(2021),
                    ),
                    SamletSkattegrunnlagForÅr(
                        utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                            oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                            inntektsår = Year.of(2022),
                        ),
                        oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                            oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                            inntektsår = Year.of(2022),
                        ),
                        år = Year.of(2022),
                    ),
                    SamletSkattegrunnlagForÅr(
                        utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                            oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                            inntektsår = Year.of(2023),
                        ),
                        oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                            oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                            inntektsår = Year.of(2023),
                        ),
                        år = Year.of(2023),
                    ),
                )
            },
        )

        mocked.service.hentSamletSkattegrunnlagForÅr(
            fnr,
            saksbehandler,
            YearRange(Year.of(2021), Year.of(2023)),
        ).let {
            it shouldBe nySkattegrunnlag(
                id = it.id,
                fnr = fnr,
                saksbehandler = saksbehandler,
                årsgrunnlag = nonEmptyListOf(
                    SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                        inntektsår = Year.of(2021),
                    ),
                    SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                        inntektsår = Year.of(2022),
                    ),
                    SamletSkattegrunnlagForÅrOgStadie.Utkast(
                        oppslag = KunneIkkeHenteSkattemelding.FinnesIkke.left(),
                        inntektsår = Year.of(2023),
                    ),
                ),
                clock = fixedClock,
                hentetTidspunkt = fixedTidspunkt,
                årSpurtFor = YearRange(Year.of(2021), Year.of(2023)),
            )
        }
    }

    @Test
    fun `henter skattedata for et år (2021)`() {
        val år = Year.of(2021)
        val mocked = mockedServices(
            mock {
                on { hentSamletSkattegrunnlagForÅrsperiode(any(), any()) } doReturn nonEmptyListOf(
                    SamletSkattegrunnlagForÅr(
                        utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                            oppslag = nySkattegrunnlagForÅr().right(),
                            inntektsår = år,
                        ),
                        oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                            oppslag = nySkattegrunnlagForÅr().right(),
                            inntektsår = år,
                        ),
                        år = år,
                    ),
                )
            },
        )

        mocked.service.hentSamletSkattegrunnlagForÅr(fnr, saksbehandler, YearRange(år, år)).let {
            it shouldBe nySkattegrunnlag(it.id)
        }
    }

    @Test
    fun `henter skattedata over 3 år (2021-2023)`() {
        val mocked = mockedServices(
            mock {
                on { hentSamletSkattegrunnlagForÅrsperiode(any(), any()) } doReturn nonEmptyListOf(
                    SamletSkattegrunnlagForÅr(
                        utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                            oppslag = nySkattegrunnlagForÅr().right(),
                            inntektsår = Year.of(2021),
                        ),
                        oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                            oppslag = nySkattegrunnlagForÅr().right(),
                            inntektsår = Year.of(2021),
                        ),
                        år = Year.of(2021),
                    ),
                    SamletSkattegrunnlagForÅr(
                        utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                            oppslag = nySkattegrunnlagForÅr().right(),
                            inntektsår = Year.of(2022),
                        ),
                        oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                            oppslag = nySkattegrunnlagForÅr().right(),
                            inntektsår = Year.of(2022),
                        ),
                        år = Year.of(2022),
                    ),
                    SamletSkattegrunnlagForÅr(
                        utkast = SamletSkattegrunnlagForÅrOgStadie.Utkast(
                            oppslag = nySkattegrunnlagForÅr().right(),
                            inntektsår = Year.of(2023),
                        ),
                        oppgjør = SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                            oppslag = nySkattegrunnlagForÅr().right(),
                            inntektsår = Year.of(2023),
                        ),
                        år = Year.of(2023),
                    ),
                )
            },
        )
        mocked.service.hentSamletSkattegrunnlagForÅr(
            fnr,
            saksbehandler,
            YearRange(Year.of(2021), Year.of(2023)),
        ).let {
            it shouldBe nySkattegrunnlag(
                id = it.id,
                fnr = fnr,
                saksbehandler = saksbehandler,
                årsgrunnlag = nonEmptyListOf(
                    SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = Year.of(2021),
                    ),
                    SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = Year.of(2022),
                    ),
                    SamletSkattegrunnlagForÅrOgStadie.Oppgjør(
                        oppslag = nySkattegrunnlagForÅr().right(),
                        inntektsår = Year.of(2023),
                    ),
                ),
                clock = fixedClock,
                hentetTidspunkt = fixedTidspunkt,
                årSpurtFor = YearRange(Year.of(2021), Year.of(2023)),
            )
        }
    }

    private data class mockedServices(
        val skatteClient: Skatteoppslag = mock(),
        val søknadsbehandlingService: SøknadsbehandlingService = mock(),
        val clock: Clock = fixedClock,
    ) {
        val service = SkatteServiceImpl(skatteClient = skatteClient, clock = fixedClock)
    }
}
