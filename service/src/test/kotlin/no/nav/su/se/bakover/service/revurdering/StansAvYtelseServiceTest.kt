package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.extensions.april
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.extensions.februar
import no.nav.su.se.bakover.common.extensions.fixedClock
import no.nav.su.se.bakover.common.extensions.januar
import no.nav.su.se.bakover.common.extensions.mai
import no.nav.su.se.bakover.common.extensions.mars
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.januar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkAvTidslinjeOgSimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkFeil
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimulerStansFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalStansFeil
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.StansAvYtelseRevurdering
import no.nav.su.se.bakover.domain.revurdering.repo.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.stans.KunneIkkeIverksetteStansYtelse
import no.nav.su.se.bakover.domain.revurdering.stans.KunneIkkeStanseYtelse
import no.nav.su.se.bakover.domain.revurdering.stans.StansYtelseRequest
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakStansAvYtelse
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.VedtakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.defaultMock
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.simulering.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.utbetaling.utbetalingsRequest
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.util.UUID

internal class StansAvYtelseServiceTest {

    @Test
    fun `svarer med feil dersom vi ikke får tak i gjeldende grunnlagdata`() {
        ServiceMocks(
            sakService = mock {
                on {
                    hentSak(
                        any<UUID>(),
                        any(),
                    )
                } doReturn søknadsbehandlingIverksattAvslagUtenBeregning().first.right()
            },
        ).let {
            it.stansYtelseService.stansAvYtelse(defaultOpprettRequest()) shouldBe KunneIkkeStanseYtelse.KunneIkkeOppretteRevurdering.left()
        }
    }

    @Test
    fun `svarer med feil ved opprettelse hvis simulering feiler teknisk`() {
        val tikkendeKlokke = TikkendeKlokke(1.april(2021).fixedClock())
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = tikkendeKlokke,
        )
        ServiceMocks(
            utbetalingService = mock {
                on { simulerUtbetaling(any(), any()) } doReturn SimuleringFeilet.TekniskFeil.left()
            },
            sakService = mock {
                on { hentSak(any<UUID>(), any()) } doReturn sak.right()
            },
            clock = tikkendeKlokke,
        ).let { serviceAndMocks ->
            serviceAndMocks.stansYtelseService.stansAvYtelse(defaultOpprettRequest()) shouldBe KunneIkkeStanseYtelse.SimuleringAvStansFeilet(
                SimulerStansFeilet.KunneIkkeSimulere(SimulerUtbetalingFeilet.FeilVedSimulering(SimuleringFeilet.TekniskFeil)),
            ).left()

            verify(serviceAndMocks.sakService).hentSak(
                sakId = any(),
                sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.utbetalingService).simulerUtbetaling(
                utbetaling = argThat {
                    it.behandler shouldBe NavIdentBruker.Saksbehandler("jonny")
                },
                simuleringsperiode = argThat { it shouldBe mai(2021)..desember(2021) },
            )
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path for opprettelse`() {
        val clock = TikkendeKlokke(1.april(2021).fixedClock())
        val (sak, _) = iverksattSøknadsbehandlingUføre(
            clock = clock,
        )
        ServiceMocks(
            utbetalingService = mock {
                doAnswer { invocation ->
                    simulerUtbetaling(
                        sak,
                        invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                        invocation.getArgument(1) as Periode,
                        clock,
                    )
                }.whenever(it).simulerUtbetaling(any(), any())
            },
            sakService = mock {
                on { hentSak(any<UUID>(), any()) } doReturn sak.right()
            },
            revurderingRepo = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
            clock = clock,
        ).also { serviceAndMocks ->
            val response = serviceAndMocks.stansYtelseService.stansAvYtelse(defaultOpprettRequest())
                .getOrFail()
                .shouldBeType<StansAvYtelseRevurdering.SimulertStansAvYtelse>()

            verify(serviceAndMocks.sakService).hentSak(
                sakId = any(),
                sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.utbetalingService, times(2)).simulerUtbetaling(
                utbetaling = argThat {
                    it.erStans() shouldBe true
                    it.tidligsteDato() shouldBe 1.mai(2021)
                    it.senesteDato() shouldBe 31.desember(2021)
                    it.behandler shouldBe NavIdentBruker.Saksbehandler("jonny")
                },
                simuleringsperiode = argThat { it shouldBe mai(2021)..desember(2021) },
            )
            verify(serviceAndMocks.revurderingRepo).lagre(
                revurdering = argThat { it shouldBe response },
                transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.observer).handle(
                argThat { event ->
                    event shouldBe StatistikkEvent.Behandling.Stans.Opprettet(response)
                },
            )
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom oversendelse av stans til oppdrag feiler teknisk`() {
        val clock = tikkendeFixedClock()
        val (sak, simulertStans) = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            clock = clock,
        )

        ServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any(), any()) } doReturn sak
            },
            utbetalingService = mock {
                doAnswer { invocation ->
                    simulerUtbetaling(
                        sak = sak,
                        utbetaling = invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                        simuleringsperiode = invocation.getArgument(1) as Periode,
                        clock = clock,
                    )
                }.whenever(it).simulerUtbetaling(any(), any())
                on { klargjørUtbetaling(any(), any()) } doReturn UtbetalingFeilet.Protokollfeil.left()
            },
            clock = clock,
        ).let { serviceAndMocks ->
            serviceAndMocks.stansYtelseService.iverksettStansAvYtelse(
                revurderingId = simulertStans.id,
                attestant = attestant,
            ) shouldBe KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale(
                UtbetalStansFeil.KunneIkkeUtbetale(
                    UtbetalingFeilet.Protokollfeil,
                ),
            ).left()

            verify(serviceAndMocks.sakService).hentSakForRevurdering(
                revurderingId = simulertStans.id,
                sessionContext = TestSessionFactory.transactionContext,
            )
            verify(serviceAndMocks.utbetalingService, times(2)).simulerUtbetaling(
                utbetaling = any(),
                simuleringsperiode = argThat { it shouldBe simulertStans.periode },
            )
            verify(serviceAndMocks.utbetalingService).klargjørUtbetaling(
                utbetaling = argThat {
                    it.erStans() shouldBe true
                    it.tidligsteDato() shouldBe simulertStans.periode.fraOgMed
                    it.senesteDato() shouldBe simulertStans.periode.tilOgMed
                    it.behandler shouldBe attestant
                },
                transactionContext = argThat { TestSessionFactory.transactionContext },
            )
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path for iverksettelse`() {
        val tikkendeKlokke = TikkendeKlokke(1.januar(2022).fixedClock())
        val (sak, simulertStans) = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            clock = tikkendeKlokke,
        )

        val callback =
            mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn utbetalingsRequest.right()
            }

        val serviceAndMocks = ServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any(), any()) } doReturn sak
            },
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), any())
            },
            utbetalingService = mock {
                doAnswer { invocation ->
                    simulerUtbetaling(
                        sak,
                        invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                        invocation.getArgument(1) as Periode,
                    )
                }.whenever(it).simulerUtbetaling(any(), any())
                doAnswer { invocation ->
                    UtbetalingKlargjortForOversendelse(
                        utbetaling = (invocation.getArgument(0) as Utbetaling.SimulertUtbetaling).toOversendtUtbetaling(
                            utbetalingsRequest,
                        ),
                        callback = callback,
                    ).right()
                }.whenever(it).klargjørUtbetaling(any(), any())
            },
            vedtakService = mock {
                doNothing().whenever(it).lagreITransaksjon(any(), any())
            },
            clock = tikkendeKlokke,
        )

        val response = serviceAndMocks.stansYtelseService.iverksettStansAvYtelse(
            revurderingId = simulertStans.id,
            attestant = attestant,
        ).getOrFail()

        verify(serviceAndMocks.sakService).hentSakForRevurdering(
            revurderingId = simulertStans.id,
            sessionContext = TestSessionFactory.transactionContext,
        )
        verify(serviceAndMocks.utbetalingService, times(2)).simulerUtbetaling(
            utbetaling = any(),
            simuleringsperiode = argThat { it shouldBe simulertStans.periode },
        )
        verify(serviceAndMocks.utbetalingService).klargjørUtbetaling(
            utbetaling = argThat {
                it.erStans() shouldBe true
                it.tidligsteDato() shouldBe simulertStans.periode.fraOgMed
                it.senesteDato() shouldBe simulertStans.periode.tilOgMed
                it.behandler shouldBe attestant
            },
            transactionContext = argThat { TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.revurderingRepo).lagre(
            revurdering = argThat { it shouldBe response },
            transactionContext = argThat { TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.vedtakService).lagreITransaksjon(
            vedtak = argThat { it.shouldBeType<VedtakStansAvYtelse>() },
            tx = argThat { TestSessionFactory.transactionContext },
        )

        verify(callback).invoke(any())
        val eventCaptor = argumentCaptor<StatistikkEvent>()
        verify(serviceAndMocks.observer, times(2)).handle(eventCaptor.capture())
        val iverksatt = eventCaptor.allValues[0]
        iverksatt.shouldBeType<StatistikkEvent.Behandling.Stans.Iverksatt>().also {
            it.vedtak.shouldBeType<VedtakStansAvYtelse>()
            it.revurdering shouldBe response
        }
        eventCaptor.allValues[1].shouldBeTypeOf<StatistikkEvent.Stønadsvedtak>()
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `svarer med feil ved forsøk på å oppdatere revurderinger som ikke er av korrekt type`() {
        val (sak, enRevurdering) = beregnetRevurdering(
            stønadsperiode = Stønadsperiode.create(år(2021)),
        )
        ServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>(), any()) } doReturn sak.right()
            },
        ).let {
            it.stansYtelseService.stansAvYtelse(
                StansYtelseRequest.Oppdater(
                    sakId = sak.id,
                    saksbehandler = NavIdentBruker.Saksbehandler("sverre"),
                    fraOgMed = 1.desember(2021),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "oppdatert",
                    ),
                    revurderingId = enRevurdering.id,
                ),
            ) shouldBe KunneIkkeStanseYtelse.UgyldigTypeForOppdatering(
                BeregnetRevurdering.Innvilget::class,
            ).left()

            verify(it.sakService).hentSak(
                argThat<UUID> { it shouldBe sak.id },
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path for oppdatering`() {
        val clock = TikkendeKlokke(1.februar(2021).fixedClock())
        val (sak, eksisterende) = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            clock = clock,
            utbetalingerKjørtTilOgMed = 1.januar(2021),
        )

        ServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>(), any()) } doReturn sak.right()
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
            },
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), any())
            },
            clock = clock,
        ).let { serviceAndMocks ->
            val response = serviceAndMocks.stansYtelseService.stansAvYtelse(
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
            ).getOrFail()

            response.let { oppdatert ->
                oppdatert.periode shouldBe mars(2021)..(januar(2022))
                oppdatert.saksbehandler shouldBe NavIdentBruker.Saksbehandler("kjeks")
                oppdatert.revurderingsårsak shouldBe Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                    begrunnelse = "fjas",
                )
            }

            verify(serviceAndMocks.utbetalingService, times(2)).simulerUtbetaling(
                utbetaling = argThat {
                    it.erStans() shouldBe true
                    it.tidligsteDato() shouldBe 1.mars(2021)
                    it.senesteDato() shouldBe 31.januar(2022)
                    it.behandler shouldBe NavIdentBruker.Saksbehandler("kjeks")
                },
                simuleringsperiode = argThat { it shouldBe mars(2021)..(januar(2022)) },
            )
            verify(serviceAndMocks.sakService).hentSak(
                sakId = sak.id,
                sessionContext = TestSessionFactory.transactionContext,
            )
            verify(serviceAndMocks.revurderingRepo).lagre(
                revurdering = argThat { it shouldBe response },
                transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `får ikke iverksatt dersom simulering indikerer feilutbetaling`() {
        val tikkendeKlokke = TikkendeKlokke(1.januar(2022).fixedClock())
        val (sak, eksisterende) = simulertStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(
            clock = tikkendeKlokke,
        )
        val mockSimulering = mock<Utbetaling.SimulertUtbetaling> {
            on { simulering } doReturn simuleringFeilutbetaling(*eksisterende.periode.måneder().toTypedArray())
        }

        ServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any(), any()) } doReturn sak
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any(), any()) } doReturn mockSimulering.right()
            },
            clock = tikkendeKlokke,
        ).let {
            val response = it.stansYtelseService.iverksettStansAvYtelse(
                revurderingId = eksisterende.id,
                attestant = attestant,
            )

            response shouldBe KunneIkkeIverksetteStansYtelse.KunneIkkeUtbetale(
                UtbetalStansFeil.KunneIkkeSimulere(
                    SimulerStansFeilet.KunneIkkeSimulere(
                        SimulerUtbetalingFeilet.FeilVedKryssjekkAvTidslinjeOgSimulering(
                            KryssjekkAvTidslinjeOgSimuleringFeilet.KryssjekkFeilet(
                                KryssjekkFeil.StansMedFeilutbetaling(
                                    // Algoritmen velger den første feilen i en sortert, prioritert liste.
                                    februar(2022),
                                ),
                            ),
                        ),
                    ),
                ),
            ).left()
            verify(it.sakService).hentSakForRevurdering(
                revurderingId = eksisterende.id,
                sessionContext = TestSessionFactory.transactionContext,
            )
            verify(it.utbetalingService, times(2)).simulerUtbetaling(
                utbetaling = any(),
                simuleringsperiode = any(),
            )
            it.verifyNoMoreInteractions()
        }
    }

    private fun defaultOpprettRequest() = StansYtelseRequest.Opprett(
        sakId = UUID.randomUUID(),
        saksbehandler = NavIdentBruker.Saksbehandler("jonny"),
        fraOgMed = 1.mai(2021),
        revurderingsårsak = Revurderingsårsak.create(
            årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
            begrunnelse = "opprett",
        ),
    )

    private data class ServiceMocks(
        val utbetalingService: UtbetalingService = defaultMock(),
        val revurderingRepo: RevurderingRepo = defaultMock(),
        val vedtakService: VedtakService = defaultMock(),
        val sakService: SakService = defaultMock(),
        val clock: Clock = fixedClock,
        val sessionFactory: SessionFactory = TestSessionFactory(),
        val observer: StatistikkEventObserver = mock(),
    ) {
        val stansYtelseService = StansYtelseServiceImpl(
            utbetalingService = utbetalingService,
            revurderingRepo = revurderingRepo,
            vedtakService = vedtakService,
            sakService = sakService,
            clock = clock,
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
