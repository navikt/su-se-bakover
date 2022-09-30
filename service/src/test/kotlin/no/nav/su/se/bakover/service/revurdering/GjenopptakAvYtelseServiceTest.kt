package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.utbetaling.SimulerGjenopptakFeil
import no.nav.su.se.bakover.service.utbetaling.UtbetalGjenopptakFeil
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.iverksattSøknadsbehandlingUføre
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.oversendtGjenopptakUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.simulertGjenopptakUtbetaling
import no.nav.su.se.bakover.test.simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.UUID

internal class GjenopptakAvYtelseServiceTest {

    @Test
    fun `svarer med feil dersom sak ikke har noen vedtak`() {
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn søknadsbehandlingVilkårsvurdertUavklart().first.right()
            },
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
        }
    }

    @Test
    fun `svarer med feil dersom sak har åpen behandling`() {
        val (sak, stans) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak()
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn opprettetRevurdering(
                    revurderingsperiode = mai(2021),
                    sakOgVedtakSomKanRevurderes = sak to stans,
                ).first.right()
            },
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
            ) shouldBe KunneIkkeGjenopptaYtelse.SakHarÅpenBehandling.left()
        }
    }

    @Test
    fun `svarer med feil dersom siste vedtak ikke er en stans`() {
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn iverksattSøknadsbehandlingUføre().first.right()
            },
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
        }
    }

    @Test
    fun `svarer med feil dersom simulering feiler`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )
        val (sak, vedtak) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(periode = periode)

        RevurderingServiceMocks(
            vedtakRepo = mock {
                on { hentForSakId(any()) } doReturn listOf(vedtak)
            },
            utbetalingService = mock {
                on {
                    simulerGjenopptak(
                        any(),
                    )
                } doReturn SimulerGjenopptakFeil.KunneIkkeSimulere(SimuleringFeilet.TekniskFeil).left()
            },
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
        ).let {
            it.revurderingService.gjenopptaYtelse(
                GjenopptaYtelseRequest.Opprett(
                    sakId = sak.id,
                    saksbehandler = saksbehandler,
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "begrunnelse",
                    ),
                ),
            ) shouldBe KunneIkkeGjenopptaYtelse.KunneIkkeSimulere(SimulerGjenopptakFeil.KunneIkkeSimulere(SimuleringFeilet.TekniskFeil)).left()

            verify(it.sakService).hentSak(sak.id)
            verify(it.utbetalingService).simulerGjenopptak(
                request = SimulerUtbetalingRequest.Gjenopptak(
                    saksbehandler = saksbehandler,
                    sak = sak,
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
        val (sak, vedtak) = vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak(periode = periode)

        val serviceAndMocks = RevurderingServiceMocks(
            vedtakRepo = mock {
                on { hentForSakId(any()) } doReturn sak.vedtakListe
            },
            utbetalingService = mock {
                on { simulerGjenopptak(any()) } doReturn simulertGjenopptakUtbetaling().right()
            },
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
        )

        val response = serviceAndMocks.revurderingService.gjenopptaYtelse(
            GjenopptaYtelseRequest.Opprett(
                sakId = sak.id,
                saksbehandler = saksbehandler,
                revurderingsårsak = Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                    begrunnelse = "begrunnelse",
                ),
            ),
        ).getOrFail()

        response.saksbehandler shouldBe saksbehandler
        response.periode shouldBe periode
        response.tilRevurdering shouldBe vedtak.id
        response.revurderingsårsak shouldBe Revurderingsårsak.create(
            årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
            begrunnelse = "begrunnelse",
        )

        verify(serviceAndMocks.sakService).hentSak(sak.id)
        verify(serviceAndMocks.utbetalingService).simulerGjenopptak(
            request = argThat {
                it shouldBe SimulerUtbetalingRequest.Gjenopptak(
                    saksbehandler = saksbehandler,
                    sak = sak,
                )
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
        val revurderingGjenopptak = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(periodeForStans = periode)

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingGjenopptak.second
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { klargjørGjenopptak(any(), any()) } doReturn UtbetalGjenopptakFeil.KunneIkkeUtbetale(
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
            verify(it.utbetalingService).klargjørGjenopptak(
                request = argThat {
                    it shouldBe UtbetalRequest.Gjenopptak(
                        sakId = sakId,
                        saksbehandler = attestant,
                        simulering = revurderingGjenopptak.second.simulering,
                    )
                },
                transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
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
        val tikkendeKlokke = TikkendeKlokke(fixedClock)
        val periode = Periode.create(
            fraOgMed = LocalDate.now(tikkendeKlokke).plusMonths(1).startOfMonth(),
            tilOgMed = LocalDate.now(tikkendeKlokke).plusMonths(2).endOfMonth(),
        )
        val (sak, revurdering) = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(
            periodeForStans = periode,
            clock = tikkendeKlokke,
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurdering
            },
            vedtakRepo = mock {
                on { hentForSakId(any()) } doReturn sak.vedtakListe
            },
            utbetalingService = mock {
                on { simulerGjenopptak(any()) } doReturn simulertGjenopptakUtbetaling().right()
            },
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
        ).let {
            val response = it.revurderingService.gjenopptaYtelse(
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

            verify(it.sakService).hentSak(sak.id)
            verify(it.utbetalingService).simulerGjenopptak(
                request = argThat {
                    it shouldBe SimulerUtbetalingRequest.Gjenopptak(
                        saksbehandler = NavIdentBruker.Saksbehandler("jossi"),
                        sak = sak,
                    )
                },
            )
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
        val eksisterende = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(
            simulering = simuleringFeilutbetaling(*periode.måneder().toTypedArray()),
        ).second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn eksisterende
        }

        RevurderingServiceMocks(
            revurderingRepo = revurderingRepoMock,
        ).let {
            val response = it.revurderingService.iverksettGjenopptakAvYtelse(
                revurderingId = eksisterende.id,
                attestant = attestant,
            )

            response shouldBe KunneIkkeIverksetteGjenopptakAvYtelse.SimuleringIndikererFeilutbetaling.left()

            verify(it.revurderingRepo).hent(eksisterende.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `får ikke opprettet ny hvis det allerede eksisterer åpen revurdering for gjenopptak`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = LocalDate.now(fixedClock).plusMonths(2).endOfMonth(),
        )
        val (sak, _) = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(periodeForStans = periode)

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            vedtakRepo = mock {
                on { hentForSakId(any()) } doReturn sak.vedtakListe
            },
        ).let {
            val response = it.revurderingService.gjenopptaYtelse(
                GjenopptaYtelseRequest.Opprett(
                    sakId = sakId,
                    saksbehandler = NavIdentBruker.Saksbehandler("sverre"),
                    revurderingsårsak = Revurderingsårsak.create(
                        årsak = Revurderingsårsak.Årsak.MOTTATT_KONTROLLERKLÆRING.toString(),
                        begrunnelse = "oppdatert",
                    ),
                ),
            )

            response shouldBe KunneIkkeGjenopptaYtelse.SakHarÅpenBehandling.left()

            verify(it.sakService).hentSak(sakId)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `happy path for iverksettelse`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )
        val simulertGjenopptak = simulertGjenopptakelseAvytelseFraVedtakStansAvYtelse(periodeForStans = periode).second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn simulertGjenopptak
        }

        val utbetaling = oversendtGjenopptakUtbetalingUtenKvittering()

        val callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalGjenopptakFeil.KunneIkkeUtbetale, Utbetalingsrequest>> {
            on { it.invoke(any()) } doReturn utbetaling.utbetalingsrequest.right()
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { klargjørGjenopptak(any(), any()) } doReturn UtbetalingKlargjortForOversendelse(
                utbetaling = utbetaling,
                callback = callback,
            ).right()
        }
        val vedtakRepoMock: VedtakRepo = mock()
        val observerMock: StatistikkEventObserver = mock()

        RevurderingServiceMocks(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
            vedtakRepo = vedtakRepoMock,
        ).let {
            it.revurderingService.addObserver(observerMock)
            val response = it.revurderingService.iverksettGjenopptakAvYtelse(
                revurderingId = revurderingId,
                attestant = NavIdentBruker.Attestant(simulertGjenopptak.saksbehandler.navIdent),
            ).getOrFail("Feil med oppsett av testdata")

            verify(it.revurderingRepo).hent(simulertGjenopptak.id)
            verify(it.utbetalingService).klargjørGjenopptak(
                request = UtbetalRequest.Gjenopptak(
                    sakId = simulertGjenopptak.sakId,
                    saksbehandler = NavIdentBruker.Attestant(simulertGjenopptak.saksbehandler.navIdent),
                    simulering = simulertGjenopptak.simulering,
                ),
                transactionContext = TestSessionFactory.transactionContext,
            )

            verify(it.revurderingRepo).lagre(
                revurdering = argThat { it shouldBe response },
                transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )

            val expectedVedtak = VedtakSomKanRevurderes.from(
                revurdering = response,
                utbetalingId = utbetaling.id,
                clock = fixedClock,
            )
            verify(it.vedtakRepo).lagre(
                vedtak = argThat { vedtak ->
                    vedtak.shouldBeEqualToIgnoringFields(
                        expectedVedtak,
                        VedtakSomKanRevurderes::id,
                    )
                },
                sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(callback).invoke(any())

            val eventCaptor = argumentCaptor<StatistikkEvent.Behandling.Gjenoppta.Iverksatt>()
            verify(observerMock, times(2)).handle(eventCaptor.capture())
            val statistikkEvent = eventCaptor.allValues[0]
            statistikkEvent shouldBe StatistikkEvent.Behandling.Gjenoppta.Iverksatt(
                vedtak = VedtakSomKanRevurderes.from(
                    revurdering = response,
                    utbetalingId = utbetaling.id,
                    clock = fixedClock,
                ).copy(
                    id = statistikkEvent.vedtak.id,
                ),
            )
            eventCaptor.allValues[1].shouldBeTypeOf<StatistikkEvent.Stønadsvedtak>()
            it.verifyNoMoreInteractions()
        }
    }
}
