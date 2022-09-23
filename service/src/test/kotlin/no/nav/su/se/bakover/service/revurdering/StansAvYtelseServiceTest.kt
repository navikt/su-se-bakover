package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.equality.shouldBeEqualToIgnoringFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.startOfMonth
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.sak.SakService
import no.nav.su.se.bakover.service.utbetaling.SimulerStansFeilet
import no.nav.su.se.bakover.service.utbetaling.UtbetalStansFeil
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
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
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattAvslagUtenBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertUavklart
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
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
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn søknadsbehandlingIverksattAvslagUtenBeregning().first.right()
            },
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
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `får ikke opprettet dersom sak har åpen behandling`() {
        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn søknadsbehandlingVilkårsvurdertUavklart().first.right()
            },
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
            ) shouldBe KunneIkkeStanseYtelse.SakHarÅpenBehandling.left()
        }
    }

    @Test
    fun `svarer med feil dersom simulering feiler`() {
        val periode = Periode.create(
            fraOgMed = LocalDate.now(fixedClock).plusMonths(1).startOfMonth(),
            tilOgMed = år(2021).tilOgMed,
        )
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(periode),
        )

        RevurderingServiceMocks(
            utbetalingService = mock {
                on {
                    simulerStans(
                        any(),
                    )
                } doReturn SimulerStansFeilet.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL).left()
            },
            sakService = mock<SakService> {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
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
        val (sak, _) = vedtakSøknadsbehandlingIverksattInnvilget(
            stønadsperiode = Stønadsperiode.create(periode),
        )

        RevurderingServiceMocks(
            utbetalingService = mock {
                on { simulerStans(any()) } doReturn simulertUtbetaling().right()
            },
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            revurderingRepo = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            },
        ).also {
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
            ).getOrFail()

            verify(it.sakService).hentSak(sakId)
            verify(it.utbetalingService).simulerStans(
                request = SimulerUtbetalingRequest.Stans(
                    sakId = sakId,
                    saksbehandler = saksbehandler,
                    stansdato = 1.mai(2021),
                ),
            )
            verify(it.revurderingRepo).defaultTransactionContext()
            verify(it.revurderingRepo).lagre(argThat { it shouldBe response }, anyOrNull())
            verify(it.observer).handle(
                argThat { event ->
                    event shouldBe StatistikkEvent.Behandling.Stans.Opprettet(response)
                },
            )
            it.verifyNoMoreInteractions()
        }
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
            },
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

        val eventCaptor = argumentCaptor<StatistikkEvent.Behandling.Stans.Iverksatt>()
        verify(serviceAndMocks.observer, times(2)).handle(eventCaptor.capture())
        val iverksatt = eventCaptor.allValues[0]
        iverksatt shouldBe StatistikkEvent.Behandling.Stans.Iverksatt(
            vedtak = VedtakSomKanRevurderes.from(
                revurdering = response,
                utbetalingId = utbetaling.id,
                clock = fixedClock,
            ).copy(id = iverksatt.vedtak.id),
        )
        eventCaptor.allValues[1].shouldBeTypeOf<StatistikkEvent.Stønadsvedtak>()
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    @Disabled("https://trello.com/c/5iblmYP9/1090-endre-sperre-for-10-endring-til-%C3%A5-v%C3%A6re-en-advarsel")
    fun `svarer med feil ved forsøk på å oppdatere revurderinger som ikke er av korrekt type`() {
        val (sak, enRevurdering) = beregnetRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
            stønadsperiode = Stønadsperiode.create(år(2021)),
        )

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            utbetalingService = mock {
                on { simulerStans(any()) } doReturn simulertUtbetaling().right()
            },
            revurderingRepo = mock {
                on { hent(any()) } doReturn enRevurdering
            },
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

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSak(any<UUID>()) } doReturn sak.right()
            },
            utbetalingService = mock {
                on { simulerStans(any()) } doReturn simulertUtbetaling().right()
            },
            revurderingRepo = mock {
                on { hent(any()) } doReturn eksisterende
            },
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
                oppdatert.periode shouldBe mars(2021).rangeTo(desember(2021))
                oppdatert.saksbehandler shouldBe NavIdentBruker.Saksbehandler("kjeks")
                oppdatert.revurderingsårsak shouldBe Revurderingsårsak.create(
                    årsak = Revurderingsårsak.Årsak.MANGLENDE_KONTROLLERKLÆRING.toString(),
                    begrunnelse = "fjas",
                )
            }

            verify(it.utbetalingService).simulerStans(
                request = SimulerUtbetalingRequest.Stans(
                    sakId = sakId,
                    saksbehandler = NavIdentBruker.Saksbehandler("kjeks"),
                    stansdato = mars(2021).fraOgMed,
                ),
            )
            verify(it.sakService).hentSak(sak.id)
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

            response shouldBe KunneIkkeStanseYtelse.SakHarÅpenBehandling.left()

            verify(sakServiceMock).hentSak(sakId)
            it.verifyNoMoreInteractions()
        }
    }
}
