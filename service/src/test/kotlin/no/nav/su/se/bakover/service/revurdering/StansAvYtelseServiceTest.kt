package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.KunneIkkeKopiereGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertStansAvytelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.simulertUtbetaling
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class StansAvYtelseServiceTest {

    @Test
    fun `svarer med feil dersom vi ikke får tak i gjeldende grunnlagdata`() {
        val vedtakServiceMock = mock<VedtakService> {
            on {
                kopierGjeldendeVedtaksdata(
                    any(),
                    any(),
                )
            } doReturn KunneIkkeKopiereGjeldendeVedtaksdata.FantIngenVedtak.left()
        }

        RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
        ).let {
            it.revurderingService.stansAvYtelse(
                StansYtelseRequest(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    fraOgMed = 1.mai(2021),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ) shouldBe KunneIkkeStanseYtelse.KunneIkkeOppretteRevurdering.left()

            verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(
                sakId = sakId,
                fraOgMed = 1.mai(2021),
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom simulering feiler`() {
        val periode = Periode.create(1.mai(2021), 31.desember(2021))
        val expected = simulertStansAvytelseFraIverksattSøknadsbehandlingsvedtak(
            periode = periode,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on {
                kopierGjeldendeVedtaksdata(
                    any(),
                    any(),
                )
            } doReturn GjeldendeVedtaksdata(
                periode = periode,
                vedtakListe = nonEmptyListOf(expected.tilRevurdering),
                clock = fixedClock,
            ).right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerStans(any(), any(), any()) } doReturn SimuleringFeilet.TEKNISK_FEIL.left()
        }

        RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            utbetalingService = utbetalingServiceMock,
        ).let {
            it.revurderingService.stansAvYtelse(
                StansYtelseRequest(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    fraOgMed = 1.mai(2021),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ) shouldBe KunneIkkeStanseYtelse.SimuleringAvStansFeilet.left()

            verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(
                sakId = sakId,
                fraOgMed = 1.mai(2021),
            )
            verify(utbetalingServiceMock).simulerStans(
                sakId = sakId,
                saksbehandler = saksbehandler,
                stansDato = 1.mai(2021),
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path`() {
        val periode = Periode.create(1.mai(2021), 31.desember(2021))
        val expected = simulertStansAvytelseFraIverksattSøknadsbehandlingsvedtak(
            periode = periode,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on {
                kopierGjeldendeVedtaksdata(
                    any(),
                    any(),
                )
            } doReturn GjeldendeVedtaksdata(
                periode = periode,
                vedtakListe = nonEmptyListOf(expected.tilRevurdering),
                clock = fixedClock,
            ).right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerStans(any(), any(), any()) } doReturn simulertUtbetaling().right()
        }

        RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            utbetalingService = utbetalingServiceMock,
        ).let {
            val response = it.revurderingService.stansAvYtelse(
                StansYtelseRequest(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    fraOgMed = 1.mai(2021),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ).getOrFail("skulle gått bra")

            verify(vedtakServiceMock).kopierGjeldendeVedtaksdata(
                sakId = sakId,
                fraOgMed = 1.mai(2021),
            )
            verify(utbetalingServiceMock).simulerStans(
                sakId = sakId,
                saksbehandler = saksbehandler,
                stansDato = 1.mai(2021),
            )
            verify(it.revurderingRepo).lagre(response)
            verify(it.vilkårsvurderingService).lagre(response.id, response.vilkårsvurderinger)
            verify(it.grunnlagService).lagreFradragsgrunnlag(response.id, response.grunnlagsdata.fradragsgrunnlag)
            verify(it.grunnlagService).lagreBosituasjongrunnlag(response.id, response.grunnlagsdata.bosituasjon)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom oversendelse av stans til oppdrag feiler`() {
        val periode = Periode.create(1.mai(2021), 31.desember(2021))
        val simulertStans = simulertStansAvytelseFraIverksattSøknadsbehandlingsvedtak(
            periode = periode,
        )

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertStans
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                stansUtbetalinger(
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } doReturn UtbetalingFeilet.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL).left()
        }

        RevurderingServiceMocks(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).let {
            it.revurderingService.iverksettStansAvYtelse(
                revurderingId = revurderingId,
                attestant = NavIdentBruker.Attestant(simulertStans.saksbehandler.navIdent),
            ) shouldBe KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale(
                UtbetalingFeilet.KunneIkkeSimulere(
                    SimuleringFeilet.TEKNISK_FEIL,
                ),
            ).left()

            verify(revurderingRepoMock).hent(simulertStans.id)
            verify(utbetalingServiceMock).stansUtbetalinger(
                sakId = simulertStans.sakId,
                attestant = NavIdentBruker.Attestant(simulertStans.saksbehandler.navIdent),
                simulering = simulertStans.simulering,
                stansDato = simulertStans.periode.fraOgMed,
            )
            it.verifyNoMoreInteractions()
        }
    }
}
