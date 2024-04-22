package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.domain.tid.endOfMonth
import no.nav.su.se.bakover.common.domain.tid.fixedClock
import no.nav.su.se.bakover.common.domain.tid.januar
import no.nav.su.se.bakover.common.domain.tid.startOfMonth
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.revurdering.GjenopptaYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.fromGjenopptak
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.GjenopptaYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeSimulereGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.revurdering.repo.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.defaultMock
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.nySøknadsbehandlingMedStønadsperiode
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulering.simulerGjenopptak
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.simulertGjenopptakAvYtelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.utbetaling.utbetalingsRequest
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.vedtak.application.VedtakService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import vedtak.domain.VedtakSomKanRevurderes
import økonomi.application.utbetaling.UtbetalingService
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.KunneIkkeKlaregjøreUtbetaling
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingFeilet
import økonomi.domain.utbetaling.UtbetalingKlargjortForOversendelse
import økonomi.domain.utbetaling.Utbetalingsrequest
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class GjenopptakAvYtelseServiceTest {

    @Test
    fun `svarer med feil dersom sak ikke har noen vedtak`() {
        ServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn nySøknadsbehandlingMedStønadsperiode().first.right()
            },
        ).let {
            it.revurderingService.gjenopptaYtelse(defaultOpprettRequest()) shouldBe KunneIkkeSimulereGjenopptakAvYtelse.FantIngenVedtak.left()
        }
    }

    @Test
    fun `svarer med feil dersom siste vedtak ikke er en stans`() {
        ServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn iverksattSøknadsbehandlingUføre().first.right()
            },
        ).let {
            it.revurderingService.gjenopptaYtelse(defaultOpprettRequest()) shouldBe KunneIkkeSimulereGjenopptakAvYtelse.SisteVedtakErIkkeStans.left()
        }
    }

    @Test
    fun `svarer med feil dersom simulering feiler`() {
        val tikkendeKlokke = TikkendeKlokke(1.januar(2022).fixedClock())
        val (sak, _) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            clock = tikkendeKlokke,
            periode = år(2022),
        )
        ServiceMocks(
            utbetalingService = mock {
                on {
                    simulerUtbetaling(
                        any(),
                    )
                } doReturn SimuleringFeilet.TekniskFeil.left()
            },
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            clock = tikkendeKlokke,
        ).let {
            it.revurderingService.gjenopptaYtelse(defaultOpprettRequest(sakId = sak.id)) shouldBe KunneIkkeSimulereGjenopptakAvYtelse.KunneIkkeSimulere(
                SimuleringFeilet.TekniskFeil,
            ).left()

            verify(it.sakService).hentSak(sak.id)
            verify(it.utbetalingService).simulerUtbetaling(any())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path for opprettelse`() {
        val tikkendeKlokke = TikkendeKlokke(1.januar(2022).fixedClock())
        val (sak, vedtak) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            clock = tikkendeKlokke,
            periode = år(2022),
        )

        val serviceAndMocks = ServiceMocks(
            utbetalingService = mock {
                doAnswer { invocation ->
                    simulerUtbetaling(
                        utbetalingerPåSak = sak.utbetalinger,
                        utbetalingForSimulering = invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                        clock = tikkendeKlokke,
                    )
                }.whenever(it).simulerUtbetaling(any())
            },
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            clock = tikkendeKlokke,
        )

        val response: GjenopptaYtelseRevurdering.SimulertGjenopptakAvYtelse =
            serviceAndMocks.revurderingService.gjenopptaYtelse(defaultOpprettRequest(sakId = sak.id)).getOrFail().first

        response.saksbehandler shouldBe saksbehandler
        response.periode shouldBe vedtak.periode
        response.tilRevurdering shouldBe vedtak.id
        response.revurderingsårsak shouldBe Revurderingsårsak.create(
            årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
            begrunnelse = "begrunnelse",
        )

        verify(serviceAndMocks.sakService).hentSak(sak.id)
        verify(serviceAndMocks.utbetalingService).simulerUtbetaling(
            utbetalingForSimulering = argThat {
                it.erReaktivering() shouldBe true
                it.tidligsteDato() shouldBe vedtak.periode.fraOgMed
                it.senesteDato() shouldBe vedtak.periode.tilOgMed
                it.behandler shouldBe saksbehandler
            },
        )
        verify(serviceAndMocks.revurderingRepo).defaultTransactionContext()
        verify(serviceAndMocks.revurderingRepo).lagre(eq(response), anyOrNull())
        verify(serviceAndMocks.observer).handle(
            argThat { event ->
                event shouldBe StatistikkEvent.Behandling.Gjenoppta.Opprettet(response)
            },
        )
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `svarer med feil dersom oversendelse av gjenopptak til oppdrag feiler`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )
        val clock = tikkendeFixedClock()
        val (sak, revurderingGjenopptak) = simulertGjenopptakAvYtelseFraVedtakStansAvYtelse(
            periodeForStans = periode,
            clock = clock,
        )

        ServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            utbetalingService = mock {
                doAnswer { invocation ->
                    simulerUtbetaling(
                        utbetalingerPåSak = sak.utbetalinger,
                        utbetalingForSimulering = invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                        clock = clock,
                    )
                }.whenever(it).simulerUtbetaling(any())
                on {
                    klargjørUtbetaling(
                        any(),
                        any(),
                    )
                } doReturn KunneIkkeKlaregjøreUtbetaling.KunneIkkeLagre(RuntimeException("en feil")).left()
            },
            clock = clock,
        ).let { serviceAndMocks ->
            val response = serviceAndMocks.revurderingService.iverksettGjenopptakAvYtelse(
                revurderingId = revurderingGjenopptak.id,
                attestant = attestant,
            )

            response shouldBe KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.KunneIkkeUtbetale.left()

            verify(serviceAndMocks.sakService).hentSakForRevurdering(revurderingGjenopptak.id)
            verify(serviceAndMocks.utbetalingService).simulerUtbetaling(
                any(),
            )
            verify(serviceAndMocks.utbetalingService).klargjørUtbetaling(
                utbetaling = any(),
                transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom revurdering ikke er av korrekt type`() {
        val (sak, enRevurdering) = simulertRevurdering()

        ServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).let {
            it.revurderingService.iverksettGjenopptakAvYtelse(
                revurderingId = enRevurdering.id,
                attestant = attestant,
            ) shouldBe KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.UgyldigTilstand(faktiskTilstand = enRevurdering::class)
                .left()
        }
    }

    @Test
    fun `happy path for oppdatering`() {
        val tikkendeKlokke = TikkendeKlokke(fixedClock)
        val periode = Periode.create(
            fraOgMed = LocalDate.now(tikkendeKlokke).plusMonths(1).startOfMonth(),
            tilOgMed = LocalDate.now(tikkendeKlokke).plusMonths(2).endOfMonth(),
        )
        val (sak, revurdering) = simulertGjenopptakAvYtelseFraVedtakStansAvYtelse(
            periodeForStans = periode,
            clock = tikkendeKlokke,
        )

        val simulertUtbetaling = simulerGjenopptak(
            sak = sak,
            behandler = NavIdentBruker.Saksbehandler("jossi"),
            clock = tikkendeKlokke,
        ).getOrFail()

        ServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurdering
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn simulertUtbetaling.right()
            },
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            clock = tikkendeKlokke,
        ).let { serviceAndMocks ->
            val response = serviceAndMocks.revurderingService.gjenopptaYtelse(
                GjenopptaYtelseRequest.Oppdater(
                    sakId = sak.id,
                    revurderingId = revurdering.id,
                    saksbehandler = NavIdentBruker.Saksbehandler("jossi"),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "ny begrunnelse",
                    ),
                ),
            ).getOrFail().first

            response.saksbehandler shouldBe NavIdentBruker.Saksbehandler("jossi")
            response.periode shouldBe periode
            response.tilRevurdering shouldBe revurdering.tilRevurdering
            response.revurderingsårsak shouldBe Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                begrunnelse = "ny begrunnelse",
            )

            verify(serviceAndMocks.sakService).hentSak(sak.id)
            verify(serviceAndMocks.utbetalingService).simulerUtbetaling(
                utbetalingForSimulering = argThat {
                    it.erReaktivering() shouldBe true
                    it.tidligsteDato() shouldBe periode.fraOgMed
                    it.senesteDato() shouldBe periode.tilOgMed
                    it.behandler shouldBe NavIdentBruker.Saksbehandler("jossi")
                },
            )
            verify(serviceAndMocks.revurderingRepo).defaultTransactionContext()
            verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe response }, anyOrNull())
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path for iverksettelse`() {
        val periode = februar(2021)..desember(2021)
        val clock = tikkendeFixedClock()
        val (sak, simulertGjenopptak) = simulertGjenopptakAvYtelseFraVedtakStansAvYtelse(
            periodeForStans = periode,
            clock = clock,
        )

        val simulertUtbetaling = simulerGjenopptak(
            sak = sak,
            behandler = NavIdentBruker.Attestant(simulertGjenopptak.saksbehandler.navIdent),
            clock = clock,
        ).getOrFail()

        val callback =
            mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn utbetalingsRequest.right()
            }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any()) } doReturn simulertUtbetaling.right()
            on { klargjørUtbetaling(any(), any()) } doReturn UtbetalingKlargjortForOversendelse(
                utbetaling = simulertUtbetaling.toOversendtUtbetaling(utbetalingsRequest),
                callback = callback,
            ).right()
        }
        val vedtakServiceMock: VedtakService = mock()
        val observerMock: StatistikkEventObserver = mock()

        ServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            utbetalingService = utbetalingServiceMock,
            vedtakService = vedtakServiceMock,
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), any())
            },
            clock = clock,
        ).let { serviceAndMocks ->
            serviceAndMocks.revurderingService.addObserver(observerMock)
            val response = serviceAndMocks.revurderingService.iverksettGjenopptakAvYtelse(
                revurderingId = simulertGjenopptak.id,
                attestant = NavIdentBruker.Attestant(simulertGjenopptak.saksbehandler.navIdent),
            ).getOrFail()

            verify(serviceAndMocks.sakService).hentSakForRevurdering(simulertGjenopptak.id)
            verify(serviceAndMocks.utbetalingService).simulerUtbetaling(
                any(),
            )
            verify(serviceAndMocks.utbetalingService).klargjørUtbetaling(
                utbetaling = argThat {
                    it.erReaktivering() shouldBe true
                    it.tidligsteDato() shouldBe periode.fraOgMed
                    it.senesteDato() shouldBe periode.tilOgMed
                    it.behandler shouldBe NavIdentBruker.Attestant(simulertGjenopptak.saksbehandler.navIdent)
                },
                transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )

            verify(serviceAndMocks.revurderingRepo).lagre(
                revurdering = argThat { it shouldBe response },
                transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )

            val expectedVedtak = VedtakSomKanRevurderes.fromGjenopptak(
                revurdering = response,
                utbetalingId = simulertUtbetaling.id,
                clock = clock,
            )
            verify(serviceAndMocks.vedtakService).lagreITransaksjon(
                vedtak = argThat { vedtak ->
                    vedtak.shouldBeEqualToIgnoringFields(
                        expectedVedtak,
                        VedtakSomKanRevurderes::id,
                        VedtakSomKanRevurderes::opprettet,
                        VedtakSomKanRevurderes::avsluttetTidspunkt,
                    )
                },
                tx = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(callback).invoke(any())

            val eventCaptor = argumentCaptor<StatistikkEvent>()
            verify(observerMock, times(2)).handle(eventCaptor.capture())
            val statistikkEvent = eventCaptor.allValues[0]
            statistikkEvent.shouldBeType<StatistikkEvent.Behandling.Gjenoppta.Iverksatt>().also {
                it.vedtak.shouldBeEqualToIgnoringFields(
                    VedtakSomKanRevurderes.fromGjenopptak(
                        revurdering = response,
                        utbetalingId = simulertUtbetaling.id,
                        clock = clock,
                    ),
                    VedtakSomKanRevurderes::id,
                    VedtakSomKanRevurderes::opprettet,
                    VedtakSomKanRevurderes::avsluttetTidspunkt,
                )
            }
            eventCaptor.allValues[1].shouldBeTypeOf<StatistikkEvent.Stønadsvedtak>()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    private fun defaultOpprettRequest(
        sakId: UUID = no.nav.su.se.bakover.test.sakId,
    ) = GjenopptaYtelseRequest.Opprett(
        sakId = sakId,
        saksbehandler = saksbehandler,
        revurderingsårsak = Revurderingsårsak.create(
            årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
            begrunnelse = "begrunnelse",
        ),
    )

    private data class ServiceMocks(
        val utbetalingService: UtbetalingService = defaultMock(),
        val revurderingRepo: RevurderingRepo = defaultMock(),
        val clock: Clock = fixedClock,
        val vedtakService: VedtakService = defaultMock(),
        val sakService: SakService = defaultMock(),
        val sessionFactory: SessionFactory = TestSessionFactory(),
        val observer: StatistikkEventObserver = mock(),
    ) {
        val revurderingService = GjenopptaYtelseServiceImpl(
            utbetalingService = utbetalingService,
            revurderingRepo = revurderingRepo,
            clock = clock,
            vedtakService = vedtakService,
            sakService = sakService,
            sessionFactory = sessionFactory,
        ).apply { addObserver(observer) }

        fun all() = listOf(
            utbetalingService,
            revurderingRepo,
            vedtakService,
            sakService,
        ).toTypedArray()

        fun verifyNoMoreInteractions() {
            org.mockito.kotlin.verifyNoMoreInteractions(
                *all(),
            )
        }
    }
}
