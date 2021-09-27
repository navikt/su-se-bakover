package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.service.utbetaling.SimulerStansFeilet
import no.nav.su.se.bakover.service.utbetaling.UtbetalStansFeil
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.KunneIkkeKopiereGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.beregnetRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.periodeMars2021
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.simulertUtbetaling
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.LocalDate

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
                StansYtelseRequest.Opprett(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    fraOgMed = 1.mai(2021),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ) shouldBe KunneIkkeStanseYtelse.KunneIkkeOppretteRevurdering.left()

            verify(it.vedtakService).kopierGjeldendeVedtaksdata(
                sakId = sakId,
                fraOgMed = 1.mai(2021),
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom simulering feiler`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = periode2021.tilOgMed,
        )
        val expected = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = periode,
        ).second

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
            on {
                simulerStans(
                    any(),
                    any(),
                    any(),
                )
            } doReturn SimulerStansFeilet.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL).left()
        }

        RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            utbetalingService = utbetalingServiceMock,
        ).let {
            it.revurderingService.stansAvYtelse(
                StansYtelseRequest.Opprett(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    fraOgMed = 1.mai(2021),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ) shouldBe KunneIkkeStanseYtelse.SimuleringAvStansFeilet(
                SimulerStansFeilet.KunneIkkeSimulere(
                    SimuleringFeilet.TEKNISK_FEIL,
                ),
            ).left()

            verify(it.vedtakService).kopierGjeldendeVedtaksdata(
                sakId = sakId,
                fraOgMed = 1.mai(2021),
            )
            verify(it.utbetalingService).simulerStans(
                sakId = sakId,
                saksbehandler = saksbehandler,
                stansDato = 1.mai(2021),
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path for opprettelse`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = periode2021.tilOgMed,
        )
        val expected = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = periode,
        ).second

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
                StansYtelseRequest.Opprett(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    fraOgMed = 1.mai(2021),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ).getOrFail("skulle gått bra")

            verify(it.vedtakService).kopierGjeldendeVedtaksdata(
                sakId = sakId,
                fraOgMed = 1.mai(2021),
            )
            verify(it.utbetalingService).simulerStans(
                sakId = sakId,
                saksbehandler = saksbehandler,
                stansDato = 1.mai(2021),
            )
            verify(it.revurderingRepo).lagre(response)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom oversendelse av stans til oppdrag feiler`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = periode2021.tilOgMed,
        )
        val simulertStans = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = periode,
        ).second

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
            } doReturn UtbetalStansFeil.KunneIkkeSimulere(SimulerStansFeilet.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL))
                .left()
        }

        RevurderingServiceMocks(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).let {
            it.revurderingService.iverksettStansAvYtelse(
                revurderingId = revurderingId,
                attestant = NavIdentBruker.Attestant(simulertStans.saksbehandler.navIdent),
            ) shouldBe KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale(
                UtbetalStansFeil.KunneIkkeSimulere(
                    SimulerStansFeilet.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL),
                ),
            ).left()

            verify(it.revurderingRepo).hent(simulertStans.id)
            verify(it.utbetalingService).stansUtbetalinger(
                sakId = simulertStans.sakId,
                attestant = NavIdentBruker.Attestant(simulertStans.saksbehandler.navIdent),
                simulering = simulertStans.simulering,
                stansDato = simulertStans.periode.fraOgMed,
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil ved forsøk på å oppdatere revurderinger som ikke er av korrekt type`() {
        val enRevurdering = beregnetRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
            stønadsperiode = Stønadsperiode.create(periode2021, "jambo"),
        ).second

        val vedtakServiceMock = mock<VedtakService> {
            on {
                kopierGjeldendeVedtaksdata(
                    any(),
                    any(),
                )
            } doReturn GjeldendeVedtaksdata(
                periode = periode2021,
                vedtakListe = nonEmptyListOf(enRevurdering.tilRevurdering),
                clock = fixedClock,
            ).right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerStans(any(), any(), any()) } doReturn simulertUtbetaling().right()
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn enRevurdering
        }

        RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            utbetalingService = utbetalingServiceMock,
            revurderingRepo = revurderingRepoMock,
        ).let {
            val response = it.revurderingService.stansAvYtelse(
                StansYtelseRequest.Oppdater(
                    sakId = sakId,
                    saksbehandler = NavIdentBruker.Saksbehandler("sverre"),
                    fraOgMed = 1.desember(2021),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "oppdatert",
                    ),
                    revurderingId = enRevurdering.id,
                ),
            )

            response shouldBe KunneIkkeStanseYtelse.UgyldigTypeForOppdatering(BeregnetRevurdering.IngenEndring::class)
                .left()

            verify(it.vedtakService).kopierGjeldendeVedtaksdata(
                sakId = sakId,
                fraOgMed = 1.desember(2021),
            )
            verify(it.utbetalingService).simulerStans(
                sakId = sakId,
                saksbehandler = NavIdentBruker.Saksbehandler("sverre"),
                stansDato = 1.desember(2021),
            )
            verify(it.revurderingRepo).hent(enRevurdering.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path for oppdatering`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = periode2021.tilOgMed,
        )
        val eksisterende = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = periode,
        ).second

        val vedtakServiceMock = mock<VedtakService> {
            on {
                kopierGjeldendeVedtaksdata(
                    any(),
                    any(),
                )
            } doReturn GjeldendeVedtaksdata(
                periode = periodeMars2021,
                vedtakListe = nonEmptyListOf(eksisterende.tilRevurdering),
                clock = fixedClock,
            ).right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerStans(any(), any(), any()) } doReturn simulertUtbetaling().right()
        }

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn eksisterende
        }

        RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            utbetalingService = utbetalingServiceMock,
            revurderingRepo = revurderingRepoMock,
        ).let {
            val response = it.revurderingService.stansAvYtelse(
                StansYtelseRequest.Oppdater(
                    sakId = sakId,
                    saksbehandler = NavIdentBruker.Saksbehandler("kjeks"),
                    fraOgMed = periodeMars2021.fraOgMed,
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "fjas",
                    ),
                    revurderingId = eksisterende.id,
                ),
            ).getOrFail("skulle gått bra")

            response.let { oppdatert ->
                oppdatert.periode shouldBe periodeMars2021
                oppdatert.saksbehandler shouldBe NavIdentBruker.Saksbehandler("kjeks")
                oppdatert.revurderingsårsak shouldBe Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                    begrunnelse = "fjas",
                )
            }

            verify(it.vedtakService).kopierGjeldendeVedtaksdata(
                sakId = sakId,
                fraOgMed = periodeMars2021.fraOgMed,
            )
            verify(it.utbetalingService).simulerStans(
                sakId = sakId,
                saksbehandler = NavIdentBruker.Saksbehandler("kjeks"),
                stansDato = periodeMars2021.fraOgMed,
            )
            verify(it.revurderingRepo).hent(eksisterende.id)
            verify(it.revurderingRepo).lagre(response)
            it.verifyNoMoreInteractions()
        }
    }
}
