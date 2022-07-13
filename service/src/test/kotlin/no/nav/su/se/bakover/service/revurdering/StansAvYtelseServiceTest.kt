package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.GjeldendeVedtaksdata
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.utbetaling.SimulerStansFeilet
import no.nav.su.se.bakover.service.utbetaling.UtbetalStansFeil
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.KunneIkkeKopiereGjeldendeVedtaksdata
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.beregnetRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.oversendtStansUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.simulertUtbetaling
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.capture
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.UUID

internal class StansAvYtelseServiceTest {

    @Test
    fun `svarer med feil dersom vi ikke får tak i gjeldende grunnlagdata`() {
        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn mock<Sak>().right()
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
            vedtakService = vedtakServiceMock,
            sakService = sakServiceMock,
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

            verify(it.sakService).hentSak(sakId)
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
            tilOgMed = år(2021).tilOgMed,
        )
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(periode),
        )

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn sak.right()
        }

        val vedtakServiceMock = mock<VedtakService> {
            on {
                kopierGjeldendeVedtaksdata(
                    any(),
                    any(),
                )
            } doReturn GjeldendeVedtaksdata(
                periode = periode,
                vedtakListe = nonEmptyListOf(vedtak),
                clock = fixedClock,
            ).right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                simulerStans(
                    any(),
                )
            } doReturn SimulerStansFeilet.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL).left()
        }

        RevurderingServiceMocks(
            vedtakService = vedtakServiceMock,
            utbetalingService = utbetalingServiceMock,
            sakService = sakServiceMock,
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

            verify(it.sakService).hentSak(sakId)
            verify(it.vedtakService).kopierGjeldendeVedtaksdata(
                sakId = sakId,
                fraOgMed = 1.mai(2021),
            )
            verify(it.utbetalingService).simulerStans(
                request = SimulerUtbetalingRequest.Stans(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    stansdato = 1.mai(2021),
                ),
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path for opprettelse`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(periode),
        )

        val serviceAndMocks = RevurderingServiceMocks(
            vedtakService = mock {
                on { kopierGjeldendeVedtaksdata(any(), any()) } doReturn GjeldendeVedtaksdata(
                    periode = periode,
                    vedtakListe = nonEmptyListOf(vedtak),
                    clock = fixedClock,
                ).right()
            },
            utbetalingService = mock {
                on { simulerStans(any()) } doReturn simulertUtbetaling().right()
            },
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            revurderingRepo = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            }
        )

        val response = serviceAndMocks.revurderingService.stansAvYtelse(
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

        verify(serviceAndMocks.sakService).hentSak(sakId)
        verify(serviceAndMocks.vedtakService).kopierGjeldendeVedtaksdata(
            sakId = sakId,
            fraOgMed = 1.mai(2021),
        )
        verify(serviceAndMocks.utbetalingService).simulerStans(
            request = SimulerUtbetalingRequest.Stans(
                sakId = sakId,
                saksbehandler = saksbehandler,
                stansdato = 1.mai(2021),
            ),
        )
        verify(serviceAndMocks.revurderingRepo).defaultTransactionContext()
        verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe response }, anyOrNull())
        verify(serviceAndMocks.observer).handle(
            argThat { event ->
                event shouldBe Event.Statistikk.RevurderingStatistikk.Stans(response)
            },
        )
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `svarer med feil dersom oversendelse av stans til oppdrag feiler`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
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
                request = UtbetalRequest.Stans(
                    request = SimulerUtbetalingRequest.Stans(
                        sakId = simulertStans.sakId,
                        saksbehandler = NavIdentBruker.Attestant(simulertStans.saksbehandler.navIdent),
                        stansdato = simulertStans.periode.fraOgMed,
                    ),
                    simulering = simulertStans.simulering,
                ),
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path for iverksettelse`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )
        val simulertStans = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(periode = periode).second
        val utbetaling = oversendtStansUtbetalingUtenKvittering(stansDato = periode.fraOgMed)

        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertStans
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            utbetalingService = mock {
                on { stansUtbetalinger(any()) } doReturn utbetaling.right()
            },
            vedtakService = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            }
        )

        val response = serviceAndMocks.revurderingService.iverksettStansAvYtelse(
            revurderingId = revurderingId,
            attestant = NavIdentBruker.Attestant(simulertStans.saksbehandler.navIdent),
        ).getOrFail("Feil med oppsett av testdata")

        verify(serviceAndMocks.revurderingRepo).hent(simulertStans.id)
        verify(serviceAndMocks.utbetalingService).stansUtbetalinger(
            request = UtbetalRequest.Stans(
                request = SimulerUtbetalingRequest.Stans(
                    sakId = simulertStans.sakId,
                    saksbehandler = NavIdentBruker.Attestant(simulertStans.saksbehandler.navIdent),
                    stansdato = simulertStans.periode.fraOgMed,
                ),
                simulering = simulertStans.simulering,
            ),
        )
        verify(serviceAndMocks.revurderingRepo).defaultTransactionContext()
        verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe response }, anyOrNull())
        val expectedVedtak = VedtakSomKanRevurderes.from(
            revurdering = response,
            utbetalingId = utbetaling.id,
            clock = fixedClock,
        )
        verify(serviceAndMocks.vedtakService).lagre(
            argThat { vedtak ->
                vedtak.shouldBeEqualToIgnoringFields(
                    expectedVedtak,
                    VedtakSomKanRevurderes::id,
                )
            },
        )

        val eventCaptor = ArgumentCaptor.forClass(Event::class.java)
        verify(serviceAndMocks.observer, times(2)).handle(capture<Event>(eventCaptor))
        eventCaptor.allValues[0] shouldBe Event.Statistikk.RevurderingStatistikk.Stans(response)
        eventCaptor.allValues[1].shouldBeTypeOf<Event.Statistikk.Vedtaksstatistikk>()
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
    fun `svarer med feil ved forsøk på å oppdatere revurderinger som ikke er av korrekt type`() {
        val (sak, enRevurdering) = beregnetRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
            stønadsperiode = Stønadsperiode.create(år(2021)),
        )

        val vedtakServiceMock = mock<VedtakService> {
            on {
                kopierGjeldendeVedtaksdata(
                    any(),
                    any(),
                )
            } doReturn sak.kopierGjeldendeVedtaksdata(
                fraOgMed = år(2021).fraOgMed,
                clock = fixedClock
            ).getOrFail().right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerStans(any()) } doReturn simulertUtbetaling().right()
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
                request = SimulerUtbetalingRequest.Stans(
                    sakId = sakId,
                    saksbehandler = NavIdentBruker.Saksbehandler("sverre"),
                    stansdato = 1.desember(2021),
                ),
            )
            verify(it.revurderingRepo).hent(enRevurdering.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path for oppdatering`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )
        val (sak, eksisterende) = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = periode,
        )

        val vedtakServiceMock = mock<VedtakService> {
            on {
                kopierGjeldendeVedtaksdata(
                    any(),
                    any(),
                )
            } doReturn sak.hentGjeldendeVedtaksdata(
                periode = mars(2021),
                clock = fixedClock
            ).getOrFail().right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerStans(any()) } doReturn simulertUtbetaling().right()
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
                    fraOgMed = mars(2021).fraOgMed,
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "fjas",
                    ),
                    revurderingId = eksisterende.id,
                ),
            ).getOrFail("skulle gått bra")

            response.let { oppdatert ->
                oppdatert.periode shouldBe mars(2021)
                oppdatert.saksbehandler shouldBe NavIdentBruker.Saksbehandler("kjeks")
                oppdatert.revurderingsårsak shouldBe Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                    begrunnelse = "fjas",
                )
            }

            verify(it.vedtakService).kopierGjeldendeVedtaksdata(
                sakId = sakId,
                fraOgMed = mars(2021).fraOgMed,
            )
            verify(it.utbetalingService).simulerStans(
                request = SimulerUtbetalingRequest.Stans(
                    sakId = sakId,
                    saksbehandler = NavIdentBruker.Saksbehandler("kjeks"),
                    stansdato = mars(2021).fraOgMed,
                ),
            )
            verify(it.revurderingRepo).hent(eksisterende.id)
            verify(it.revurderingRepo).defaultTransactionContext()
            verify(it.revurderingRepo).lagre(argThat { it shouldBe response }, anyOrNull())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `får ikke iverksatt dersom simulering indikerer feilutbetaling`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )
        val eksisterende = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = periode,
            simulering = simuleringFeilutbetaling(*periode.måneder().toTypedArray()),
        ).second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn eksisterende
        }

        RevurderingServiceMocks(
            revurderingRepo = revurderingRepoMock,
        ).let {
            val response = it.revurderingService.iverksettStansAvYtelse(
                revurderingId = eksisterende.id,
                attestant = attestant,
            )

            response shouldBe KunneIkkeIverksetteStansYtelse.SimuleringIndikererFeilutbetaling.left()

            verify(it.revurderingRepo).hent(eksisterende.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `får ikke opprettet ny hvis det allerede eksisterer åpen revurdering for stans`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )
        val eksisterende = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            periode = periode,
        )

        val sakServiceMock = mock<SakService> {
            on { hentSak(any<UUID>()) } doReturn eksisterende.first.right()
        }

        RevurderingServiceMocks(
            sakService = sakServiceMock,
        ).let {
            val response = it.revurderingService.stansAvYtelse(
                StansYtelseRequest.Opprett(
                    sakId = sakId,
                    saksbehandler = NavIdentBruker.Saksbehandler("sverre"),
                    fraOgMed = 1.desember(2021),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "oppdatert",
                    ),
                ),
            )

            response shouldBe KunneIkkeStanseYtelse.SakHarÅpenRevurderingForStansAvYtelse.left()

            verify(sakServiceMock).hentSak(sakId)
            it.verifyNoMoreInteractions()
        }
    }
}
