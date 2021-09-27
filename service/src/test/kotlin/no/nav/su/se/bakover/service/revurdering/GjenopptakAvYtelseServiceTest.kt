package no.nav.su.se.bakover.service.revurdering

import arrow.core.NonEmptyList
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.database.revurdering.RevurderingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.utbetaling.SimulerGjenopptakFeil
import no.nav.su.se.bakover.service.utbetaling.UtbetalGjenopptakFeil
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.KunneIkkeKopiereGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.periode2021
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertGjenopptakUtbetaling
import no.nav.su.se.bakover.test.simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelse
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.LocalDate

class GjenopptakAvYtelseServiceTest {

    @Test
    fun `svarer med feil dersom sak ikke har noen vedtak`() {
        val vedtakRepoMock = mock<VedtakRepo>() {
            on { hentForSakId(any()) } doReturn emptyList()
        }

        RevurderingServiceMocks(
            vedtakRepo = vedtakRepoMock,
        ).let {
            it.revurderingService.gjenopptaYtelse(
                GjenopptaYtelseRequest.Opprett(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ) shouldBe KunneIkkeGjenopptaYtelse.FantIngenVedtak.left()

            verify(it.vedtakRepo).hentForSakId(
                sakId = sakId,
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom siste vedtak ikke er en stans`() {
        val vedtakRepoMock = mock<VedtakRepo>() {
            on { hentForSakId(any()) } doReturn listOf(vedtakSøknadsbehandlingIverksattInnvilget().second)
        }

        RevurderingServiceMocks(
            vedtakRepo = vedtakRepoMock,
        ).let {
            it.revurderingService.gjenopptaYtelse(
                GjenopptaYtelseRequest.Opprett(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ) shouldBe KunneIkkeGjenopptaYtelse.SisteVedtakErIkkeStans.left()

            verify(it.vedtakRepo).hentForSakId(
                sakId = sakId,
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom vi ikke får tak i gjeldende grunnlagdata`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = periode2021.tilOgMed,
        )
        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentForSakId(any()) } doReturn listOf(
                vedtakIverksattStansAvYtelse(periode).second,
            )
        }
        val vedtakServiceMock = mock<VedtakService> {
            on {
                kopierGjeldendeVedtaksdata(
                    any(),
                    any(),
                )
            } doReturn KunneIkkeKopiereGjeldendeVedtaksdata.FantIngenVedtak.left()
        }

        RevurderingServiceMocks(
            vedtakRepo = vedtakRepoMock,
            vedtakService = vedtakServiceMock,
        ).let {
            it.revurderingService.gjenopptaYtelse(
                GjenopptaYtelseRequest.Opprett(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ) shouldBe KunneIkkeGjenopptaYtelse.KunneIkkeOppretteRevurdering.left()

            verify(it.vedtakRepo).hentForSakId(sakId)
            verify(it.vedtakService).kopierGjeldendeVedtaksdata(
                sakId = sakId,
                fraOgMed = periode.fraOgMed,
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
        val (sak, vedtak) = vedtakIverksattStansAvYtelse(periode)

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentForSakId(any()) } doReturn listOf(vedtak)
        }

        val vedtakServiceMock = mock<VedtakService> {
            on {
                kopierGjeldendeVedtaksdata(
                    any(),
                    any(),
                )
            } doReturn GjeldendeVedtaksdata(
                periode = periode,
                vedtakListe = NonEmptyList.fromListUnsafe(sak.vedtakListe.filterIsInstance<VedtakSomKanRevurderes>()),
                clock = fixedClock,
            ).right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                simulerGjenopptak(
                    any(),
                    any(),
                )
            } doReturn SimulerGjenopptakFeil.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL).left()
        }

        RevurderingServiceMocks(
            vedtakRepo = vedtakRepoMock,
            vedtakService = vedtakServiceMock,
            utbetalingService = utbetalingServiceMock,
        ).let {
            it.revurderingService.gjenopptaYtelse(
                GjenopptaYtelseRequest.Opprett(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ) shouldBe KunneIkkeGjenopptaYtelse.KunneIkkeSimulere(
                SimulerGjenopptakFeil.KunneIkkeSimulere(
                    SimuleringFeilet.TEKNISK_FEIL,
                ),
            ).left()

            verify(it.vedtakRepo).hentForSakId(sakId)
            verify(it.vedtakService).kopierGjeldendeVedtaksdata(
                sakId = sakId,
                fraOgMed = periode.fraOgMed,
            )
            verify(it.utbetalingService).simulerGjenopptak(
                sakId = sakId,
                saksbehandler = saksbehandler,
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
        val vedtakStans = vedtakIverksattStansAvYtelse(periode)

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentForSakId(any()) } doReturn vedtakStans.first.vedtakListe
        }

        val vedtakServiceMock = mock<VedtakService> {
            on {
                kopierGjeldendeVedtaksdata(
                    any(),
                    any(),
                )
            } doReturn GjeldendeVedtaksdata(
                periode = periode,
                vedtakListe = nonEmptyListOf(vedtakStans.second),
                clock = fixedClock,
            ).right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerGjenopptak(any(), any()) } doReturn simulertGjenopptakUtbetaling().right()
        }

        RevurderingServiceMocks(
            vedtakRepo = vedtakRepoMock,
            vedtakService = vedtakServiceMock,
            utbetalingService = utbetalingServiceMock,
        ).let {
            val response = it.revurderingService.gjenopptaYtelse(
                GjenopptaYtelseRequest.Opprett(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ).getOrFail("skulle gått bra")

            response.saksbehandler shouldBe saksbehandler
            response.periode shouldBe periode
            response.tilRevurdering shouldBe vedtakStans.second
            response.revurderingsårsak shouldBe Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                begrunnelse = "begrunnelse",
            )

            verify(it.vedtakRepo).hentForSakId(sakId)
            verify(it.vedtakService).kopierGjeldendeVedtaksdata(
                sakId = sakId,
                fraOgMed = periode.fraOgMed,
            )
            verify(it.utbetalingService).simulerGjenopptak(
                sakId = sakId,
                saksbehandler = saksbehandler,
            )
            verify(it.revurderingRepo).lagre(response)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom oversendelse av gjenopptak til oppdrag feiler`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = periode2021.tilOgMed,
        )
        val revurderingGjenopptak = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(periode)

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingGjenopptak.second
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { gjenopptaUtbetalinger(any(), any(), any()) } doReturn UtbetalGjenopptakFeil.KunneIkkeUtbetale(
                UtbetalingFeilet.Protokollfeil,
            ).left()
        }

        RevurderingServiceMocks(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
        ).let {
            val response = it.revurderingService.iverksettGjenopptakAvYtelse(
                revurderingId = revurderingGjenopptak.second.id,
                attestant = attestant,
            )

            response shouldBe KunneIkkeIverksetteGjenopptakAvYtelse.KunneIkkeUtbetale(
                UtbetalGjenopptakFeil.KunneIkkeUtbetale(
                    UtbetalingFeilet.Protokollfeil,
                ),
            ).left()

            verify(revurderingRepoMock).hent(revurderingGjenopptak.second.id)
            verify(it.utbetalingService).gjenopptaUtbetalinger(
                sakId = sakId,
                attestant = attestant,
                simulering = revurderingGjenopptak.second.simulering,
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom revurdering ikke er av korrekt type`() {
        val enRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak()

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn enRevurdering.second
        }

        RevurderingServiceMocks(
            revurderingRepo = revurderingRepoMock,
        ).let {
            val response = it.revurderingService.iverksettGjenopptakAvYtelse(
                revurderingId = enRevurdering.second.id,
                attestant = attestant,
            )

            response shouldBe KunneIkkeIverksetteGjenopptakAvYtelse.UgyldigTilstand(
                faktiskTilstand = enRevurdering.second::class,
            ).left()

            verify(revurderingRepoMock).hent(enRevurdering.second.id)

            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path for oppdatering`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = LocalDate.now(fixedClock).plusMonths(2).endOfMonth(),
        )
        val eksisterende = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(periode)

        val vedtakRepoMock = mock<VedtakRepo> {
            on { hentForSakId(any()) } doReturn eksisterende.first.vedtakListe
        }

        val vedtakServiceMock = mock<VedtakService> {
            on {
                kopierGjeldendeVedtaksdata(
                    any(),
                    any(),
                )
            } doReturn GjeldendeVedtaksdata(
                periode = periode,
                vedtakListe = nonEmptyListOf(eksisterende.second.tilRevurdering),
                clock = fixedClock,
            ).right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerGjenopptak(any(), any()) } doReturn simulertGjenopptakUtbetaling().right()
        }

        val revurderingRepoMock = mock<RevurderingRepo>() {
            on { hent(any()) } doReturn eksisterende.second
        }

        RevurderingServiceMocks(
            revurderingRepo = revurderingRepoMock,
            vedtakRepo = vedtakRepoMock,
            vedtakService = vedtakServiceMock,
            utbetalingService = utbetalingServiceMock,
        ).let {
            val response = it.revurderingService.gjenopptaYtelse(
                GjenopptaYtelseRequest.Oppdater(
                    sakId = sakId,
                    revurderingId = eksisterende.second.id,
                    saksbehandler = NavIdentBruker.Saksbehandler("jossi"),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "ny begrunnelse",
                    ),
                ),
            ).getOrFail("skulle gått bra")

            response.saksbehandler shouldBe NavIdentBruker.Saksbehandler("jossi")
            response.periode shouldBe periode
            response.tilRevurdering shouldBe eksisterende.second.tilRevurdering
            response.revurderingsårsak shouldBe Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                begrunnelse = "ny begrunnelse",
            )

            verify(vedtakRepoMock).hentForSakId(sakId)
            verify(it.vedtakService).kopierGjeldendeVedtaksdata(
                sakId = sakId,
                fraOgMed = periode.fraOgMed,
            )
            verify(it.utbetalingService).simulerGjenopptak(
                sakId = sakId,
                saksbehandler = NavIdentBruker.Saksbehandler("jossi"),
            )
            verify(it.revurderingRepo).hent(eksisterende.second.id)
            verify(it.revurderingRepo).lagre(response)
            it.verifyNoMoreInteractions()
        }
    }
}
