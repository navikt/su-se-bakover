package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.april
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.fixedClock
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkAvTidslinjeOgSimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkFeil
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerGjenopptakFeil
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalGjenopptakFeil
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.GjenopptaYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering
import no.nav.su.se.bakover.domain.revurdering.gjenopptak.KunneIkkeSimulereGjenopptakAvYtelse
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.defaultMock
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulerGjenopptak
import no.nav.su.se.bakover.test.simulerUtbetaling
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.utbetalingsRequest
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
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
import java.time.Clock
import java.time.LocalDate
import java.util.UUID

internal class GjenopptakAvYtelseServiceTest {

    @Test
    fun `svarer med feil dersom sak ikke har noen vedtak`() {
        ServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn søknadsbehandlingVilkårsvurdertUavklart().first.right()
            },
        ).let {
            it.revurderingService.gjenopptaYtelse(defaultOpprettRequest()) shouldBe KunneIkkeSimulereGjenopptakAvYtelse.FantIngenVedtak.left()
        }
    }

    @Test
    fun `svarer med feil dersom sak har åpen behandling`() {
        val tikkendeKlokke = TikkendeKlokke(1.januar(2022).fixedClock())
        val (sak, stans) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            clock = tikkendeKlokke,
        )

        ServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn opprettetRevurdering(
                    revurderingsperiode = mai(2022),
                    sakOgVedtakSomKanRevurderes = sak to stans,
                    clock = tikkendeKlokke,
                ).first.right()
            },
            clock = tikkendeKlokke,
        ).let {
            it.revurderingService.gjenopptaYtelse(defaultOpprettRequest()) shouldBe KunneIkkeSimulereGjenopptakAvYtelse.SakHarÅpenBehandling.left()
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
        val (sak, vedtak) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            clock = tikkendeKlokke,
        )

        ServiceMocks(
            vedtakRepo = mock {
                on { hentForSakId(any()) } doReturn listOf(vedtak)
            },
            utbetalingService = mock {
                on {
                    simulerUtbetaling(
                        any(),
                        any(),
                    )
                } doReturn SimuleringFeilet.TekniskFeil.left()
            },
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            clock = tikkendeKlokke,
        ).let {
            it.revurderingService.gjenopptaYtelse(defaultOpprettRequest()) shouldBe KunneIkkeSimulereGjenopptakAvYtelse.KunneIkkeSimulere(
                SimulerGjenopptakFeil.KunneIkkeSimulere(SimulerUtbetalingFeilet.FeilVedSimulering(SimuleringFeilet.TekniskFeil)),
            ).left()

            verify(it.sakService).hentSak(sak.id)
            verify(it.utbetalingService).simulerUtbetaling(any(), any())
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path for opprettelse`() {
        val tikkendeKlokke = TikkendeKlokke(1.januar(2022).fixedClock())
        val (sak, vedtak) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            clock = tikkendeKlokke,
        )

        val serviceAndMocks = ServiceMocks(
            vedtakRepo = mock {
                on { hentForSakId(any()) } doReturn sak.vedtakListe
            },
            utbetalingService = mock {
                doAnswer { invocation ->
                    simulerUtbetaling(
                        sak,
                        invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                        invocation.getArgument(1) as Periode,
                        tikkendeKlokke,
                    )
                }.whenever(it).simulerUtbetaling(any(), any())
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

        val response = serviceAndMocks.revurderingService.gjenopptaYtelse(defaultOpprettRequest()).getOrFail()

        response.saksbehandler shouldBe saksbehandler
        response.periode shouldBe vedtak.periode
        response.tilRevurdering shouldBe vedtak.id
        response.revurderingsårsak shouldBe Revurderingsårsak.create(
            årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
            begrunnelse = "begrunnelse",
        )

        verify(serviceAndMocks.sakService).hentSak(sak.id)
        verify(serviceAndMocks.utbetalingService, times(2)).simulerUtbetaling(
            utbetaling = argThat {
                it.erReaktivering() shouldBe true
                it.tidligsteDato() shouldBe vedtak.periode.fraOgMed
                it.senesteDato() shouldBe vedtak.periode.tilOgMed
                it.behandler shouldBe saksbehandler
            },
            simuleringsperiode = argThat { it shouldBe vedtak.periode },
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
        val (sak, revurderingGjenopptak) = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(
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
                        sak,
                        invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                        invocation.getArgument(1) as Periode,
                        clock,
                    )
                }.whenever(it).simulerUtbetaling(any(), any())
                on { klargjørUtbetaling(any(), any()) } doReturn UtbetalingFeilet.Protokollfeil.left()
            },
            clock = clock,
        ).let { serviceAndMocks ->
            val response = serviceAndMocks.revurderingService.iverksettGjenopptakAvYtelse(
                revurderingId = revurderingGjenopptak.id,
                attestant = attestant,
            )

            response shouldBe KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.KunneIkkeUtbetale(
                UtbetalGjenopptakFeil.KunneIkkeUtbetale(
                    UtbetalingFeilet.Protokollfeil,
                ),
            ).left()

            verify(serviceAndMocks.sakService).hentSakForRevurdering(revurderingGjenopptak.id)
            verify(serviceAndMocks.utbetalingService, times(2)).simulerUtbetaling(
                any(),
                argThat { it shouldBe periode },
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
        val (sak, enRevurdering) = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak()

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
        val (sak, revurdering) = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(
            periodeForStans = periode,
            clock = tikkendeKlokke,
        )

        val simulertUtbetaling = simulerGjenopptak(
            sak = sak,
            gjenopptak = revurdering,
            behandler = NavIdentBruker.Saksbehandler("jossi"),
            clock = tikkendeKlokke,
        ).getOrFail()

        ServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurdering
            },
            vedtakRepo = mock {
                on { hentForSakId(any()) } doReturn sak.vedtakListe
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any(), any()) } doReturn simulertUtbetaling.right()
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
            ).getOrFail("skulle gått bra")

            response.saksbehandler shouldBe NavIdentBruker.Saksbehandler("jossi")
            response.periode shouldBe periode
            response.tilRevurdering shouldBe revurdering.tilRevurdering
            response.revurderingsårsak shouldBe Revurderingsårsak.create(
                årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                begrunnelse = "ny begrunnelse",
            )

            verify(serviceAndMocks.sakService).hentSak(sak.id)
            verify(serviceAndMocks.utbetalingService, times(2)).simulerUtbetaling(
                utbetaling = argThat {
                    it.erReaktivering() shouldBe true
                    it.tidligsteDato() shouldBe periode.fraOgMed
                    it.senesteDato() shouldBe periode.tilOgMed
                    it.behandler shouldBe NavIdentBruker.Saksbehandler("jossi")
                },
                simuleringsperiode = argThat { it shouldBe periode },
            )
            verify(serviceAndMocks.revurderingRepo).defaultTransactionContext()
            verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe response }, anyOrNull())
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `får ikke iverksatt dersom simulering indikerer feilutbetaling`() {
        val clock = TikkendeKlokke(1.april(2021).fixedClock())
        val (sak, eksisterende) = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(
            clock = clock,
            periodeForStans = mai(2021),
            utbetalingerKjørtTilOgMed = 1.april(2021),
        )

        val mockSimulering = mock<Utbetaling.SimulertUtbetaling> {
            on { simulering } doReturn simuleringFeilutbetaling(*eksisterende.periode.måneder().toTypedArray())
        }

        ServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any(), any()) } doReturn mockSimulering.right()
            },
            clock = clock,
        ).let {
            val response = it.revurderingService.iverksettGjenopptakAvYtelse(
                revurderingId = eksisterende.id,
                attestant = attestant,
            )
            response shouldBe KunneIkkeIverksetteGjenopptakAvYtelseForRevurdering.KunneIkkeUtbetale(
                UtbetalGjenopptakFeil.KunneIkkeSimulere(
                    SimulerGjenopptakFeil.KunneIkkeSimulere(
                        SimulerUtbetalingFeilet.FeilVedKryssjekkAvTidslinjeOgSimulering(
                            KryssjekkAvTidslinjeOgSimuleringFeilet.KryssjekkFeilet(
                                KryssjekkFeil.GjenopptakMedFeilutbetaling,
                            ),
                        ),
                    ),
                ),
            ).left()

            verify(it.sakService).hentSakForRevurdering(eksisterende.id)
            verify(it.utbetalingService, times(2)).simulerUtbetaling(
                utbetaling = any(),
                simuleringsperiode = any(),
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `får ikke opprettet ny hvis det allerede eksisterer åpen revurdering for gjenopptak`() {
        val (sak, _) = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse()

        ServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            vedtakRepo = mock {
                on { hentForSakId(any()) } doReturn sak.vedtakListe
            },
        ).let {
            it.revurderingService.gjenopptaYtelse(defaultOpprettRequest()) shouldBe KunneIkkeSimulereGjenopptakAvYtelse.SakHarÅpenBehandling.left()
        }
    }

    @Test
    fun `happy path for iverksettelse`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )
        val clock = tikkendeFixedClock()
        val (sak, simulertGjenopptak) = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(
            periodeForStans = periode,
            clock = clock,
        )

        val simulertUtbetaling = simulerGjenopptak(
            sak = sak,
            gjenopptak = simulertGjenopptak,
            behandler = NavIdentBruker.Attestant(simulertGjenopptak.saksbehandler.navIdent),
            clock = clock,
        ).getOrFail()

        val callback =
            mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn utbetalingsRequest.right()
            }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerUtbetaling(any(), any()) } doReturn simulertUtbetaling.right()
            on { klargjørUtbetaling(any(), any()) } doReturn UtbetalingKlargjortForOversendelse(
                utbetaling = simulertUtbetaling.toOversendtUtbetaling(utbetalingsRequest),
                callback = callback,
            ).right()
        }
        val vedtakRepoMock: VedtakRepo = mock()
        val observerMock: StatistikkEventObserver = mock()

        ServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            utbetalingService = utbetalingServiceMock,
            vedtakRepo = vedtakRepoMock,
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), any())
            },
            clock = clock,
        ).let { serviceAndMocks ->
            serviceAndMocks.revurderingService.addObserver(observerMock)
            val response = serviceAndMocks.revurderingService.iverksettGjenopptakAvYtelse(
                revurderingId = revurderingId,
                attestant = NavIdentBruker.Attestant(simulertGjenopptak.saksbehandler.navIdent),
            ).getOrFail()

            verify(serviceAndMocks.sakService).hentSakForRevurdering(simulertGjenopptak.id)
            verify(serviceAndMocks.utbetalingService, times(2)).simulerUtbetaling(
                any(),
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

            val expectedVedtak = VedtakSomKanRevurderes.from(
                revurdering = response,
                utbetalingId = simulertUtbetaling.id,
                clock = clock,
            )
            verify(serviceAndMocks.vedtakRepo).lagreITransaksjon(
                vedtak = argThat { vedtak ->
                    vedtak.shouldBeEqualToIgnoringFields(
                        expectedVedtak,
                        VedtakSomKanRevurderes::id,
                        VedtakSomKanRevurderes::opprettet,
                    )
                },
                sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(callback).invoke(any())

            val eventCaptor = argumentCaptor<StatistikkEvent.Behandling.Gjenoppta.Iverksatt>()
            verify(observerMock, times(2)).handle(eventCaptor.capture())
            val statistikkEvent = eventCaptor.allValues[0]
            statistikkEvent.shouldBeType<StatistikkEvent.Behandling.Gjenoppta.Iverksatt>().also {
                it.vedtak.shouldBeEqualToIgnoringFields(
                    VedtakSomKanRevurderes.from(
                        revurdering = response,
                        utbetalingId = simulertUtbetaling.id,
                        clock = clock,
                    ),
                    VedtakSomKanRevurderes::id,
                    VedtakSomKanRevurderes::opprettet,
                )
            }
            eventCaptor.allValues[1].shouldBeTypeOf<StatistikkEvent.Stønadsvedtak>()
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    private fun defaultOpprettRequest() = GjenopptaYtelseRequest.Opprett(
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
        val vedtakRepo: VedtakRepo = defaultMock(),
        val sakService: SakService = defaultMock(),
        val sessionFactory: SessionFactory = TestSessionFactory(),
        val observer: StatistikkEventObserver = mock(),
    ) {
        val revurderingService = GjenopptaYtelseServiceImpl(
            utbetalingService = utbetalingService,
            revurderingRepo = revurderingRepo,
            clock = clock,
            vedtakRepo = vedtakRepo,
            sakService = sakService,
            sessionFactory = sessionFactory,
        ).apply { addObserver(observer) }

        fun all() = listOf(
            utbetalingService,
            revurderingRepo,
            vedtakRepo,
            sakService,
        ).toTypedArray()

        fun verifyNoMoreInteractions() {
            org.mockito.kotlin.verifyNoMoreInteractions(
                *all(),
            )
        }
    }
}
