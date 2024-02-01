package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.extensions.november
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeFerdigstilleIverksettelsestransaksjon
import no.nav.su.se.bakover.domain.revurdering.iverksett.KunneIkkeIverksetteRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetRevurdering
import no.nav.su.se.bakover.domain.vedtak.VedtakOpphørMedUtbetaling
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataEnsligMedFradrag
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.simulering.simulerOpphør
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.utbetaling.nyUtbetalingOversendUtenKvittering
import no.nav.su.se.bakover.test.utbetaling.utbetalingsRequest
import no.nav.su.se.bakover.test.vedtakRevurdering
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import økonomi.domain.utbetaling.Utbetaling
import økonomi.domain.utbetaling.UtbetalingFeilet
import økonomi.domain.utbetaling.UtbetalingKlargjortForOversendelse
import økonomi.domain.utbetaling.Utbetalingsrequest
import java.util.UUID

internal class IverksettGrunnlagsdataOgVilkårsvurderingerRevurderingTest {

    @Test
    fun `iverksett - iverksetter endring av ytelse`() {
        val clock = tikkendeFixedClock()
        val sakOgVedtak = vedtakSøknadsbehandlingIverksattInnvilget(
            clock = clock,
        )
        val grunnlagsdata = grunnlagsdataEnsligMedFradrag().let { it.fradragsgrunnlag + it.bosituasjon }

        val (sak, revurderingTilAttestering) = revurderingTilAttestering(
            sakOgVedtakSomKanRevurderes = sakOgVedtak,
            clock = clock,
            grunnlagsdataOverrides = grunnlagsdata,
        )

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = nyUtbetalingOversendUtenKvittering(
                sakOgBehandling = sak to revurderingTilAttestering,
                beregning = revurderingTilAttestering.beregning,
                clock = clock,
            ),
            callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn utbetalingsRequest.right()
            },
        )

        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                doAnswer { invocation ->
                    simulerUtbetaling(
                        utbetalingerPåSak = sak.utbetalinger,
                        utbetalingForSimulering = invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                        clock = clock,
                    )
                }.whenever(it).simulerUtbetaling(any())
                on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakService = mock {
                doNothing().whenever(it).lagreITransaksjon(any(), anyOrNull())
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            clock = clock,
        )

        val response = serviceAndMocks.revurderingService.iverksett(revurderingTilAttestering.id, attestant)
            .getOrFail() as IverksattRevurdering.Innvilget

        verify(serviceAndMocks.sakService).hentSakForRevurdering(argThat { it shouldBe revurderingTilAttestering.id })
        verify(serviceAndMocks.utbetalingService).simulerUtbetaling(any())
        verify(serviceAndMocks.utbetalingService).klargjørUtbetaling(
            utbetaling = any(),
            transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.vedtakService).lagreITransaksjon(
            vedtak = argThat { it should beOfType<VedtakInnvilgetRevurdering>() },
            tx = argThat { TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.revurderingRepo).lagre(
            revurdering = argThat { it shouldBe response },
            transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(utbetalingKlargjortForOversendelse.callback).invoke(utbetalingsRequest)

        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `iverksett - iverksetter opphør av ytelse`() {
        val clock = tikkendeFixedClock()
        val (sak, revurdering) = revurderingTilAttestering(
            clock = clock,
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
        ).let { (sak, revurdering) -> sak to revurdering as RevurderingTilAttestering.Opphørt }

        val simulertUtbetaling = simulerOpphør(
            sak = sak,
            revurdering = revurdering,
            clock = clock,
        ).getOrFail()

        val callback =
            mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn utbetalingsRequest.right()
            }

        val utbetalingKlarForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = simulertUtbetaling.toOversendtUtbetaling(utbetalingsRequest),
            callback = callback,
        )

        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn simulertUtbetaling.right()
                on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlarForOversendelse.right()
            },
            vedtakService = mock {
                doNothing().whenever(it).lagreITransaksjon(any(), anyOrNull())
            },
            annullerKontrollsamtaleService = mock {},
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            clock = clock,
        )

        serviceAndMocks.revurderingService.iverksett(revurdering.id, attestant)

        verify(serviceAndMocks.sakService).hentSakForRevurdering(revurdering.id)
        verify(serviceAndMocks.utbetalingService).simulerUtbetaling(
            any(),
        )
        verify(serviceAndMocks.utbetalingService).klargjørUtbetaling(
            utbetaling = any(),
            transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.vedtakService).lagreITransaksjon(
            vedtak = argThat { it should beOfType<VedtakOpphørMedUtbetaling>() },
            tx = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.annullerKontrollsamtaleService).annuller(
            sakId = argThat { it shouldBe revurdering.sakId },
            sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.revurderingRepo).lagre(
            revurdering = any(),
            transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(callback).invoke(utbetalingKlarForOversendelse.utbetaling.utbetalingsrequest)
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `iverksett opphør - opphøret skal publiseres etter alle databasekallene`() {
        val clock = tikkendeFixedClock()
        val (sak, revurderingTilAttestering) = revurderingTilAttestering(
            clock = clock,
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
        ).let { (sak, revurdering) -> sak to revurdering as RevurderingTilAttestering.Opphørt }

        val simulertUtbetaling = simulerOpphør(
            sak = sak,
            revurdering = revurderingTilAttestering,
            clock = clock,
        ).getOrFail()

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = simulertUtbetaling.toOversendtUtbetaling(utbetalingsRequest),
            callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn utbetalingsRequest.right()
            },
        )

        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn simulertUtbetaling.right()
                on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakService = mock {
                doNothing().whenever(it).lagreITransaksjon(any(), anyOrNull())
            },
            annullerKontrollsamtaleService = mock {},
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            clock = clock,
        )

        serviceAndMocks.revurderingService.iverksett(revurderingTilAttestering.id, attestant)

        inOrder(*serviceAndMocks.all(), utbetalingKlargjortForOversendelse.callback) {
            verify(serviceAndMocks.utbetalingService).simulerUtbetaling(
                any(),
            )
            verify(serviceAndMocks.utbetalingService).klargjørUtbetaling(
                any(),
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.vedtakService).lagreITransaksjon(
                any(),
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.annullerKontrollsamtaleService).annuller(
                any(),
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.revurderingRepo).lagre(
                any(),
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(utbetalingKlargjortForOversendelse.callback).invoke(utbetalingsRequest)
        }
    }

    @Test
    fun `iverksett innvilget - utbetaling skal publiseres etter alle databasekallene`() {
        val clock = tikkendeFixedClock()
        val (sak, revurderingTilAttestering) = revurderingTilAttestering(
            clock = clock,
        )

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = nyUtbetalingOversendUtenKvittering(
                sakOgBehandling = sak to revurderingTilAttestering,
                beregning = revurderingTilAttestering.beregning,
                clock = clock,
            ),
            callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn utbetalingsRequest.right()
            },
        )

        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                doAnswer { invocation ->
                    simulerUtbetaling(
                        utbetalingerPåSak = sak.utbetalinger,
                        utbetalingForSimulering = invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                    )
                }.whenever(it).simulerUtbetaling(any())
                on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakService = mock {
                doNothing().whenever(it).lagreITransaksjon(any(), anyOrNull())
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            clock = clock,
        )

        serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingTilAttestering.id,
            attestant = attestant,
        )

        inOrder(*serviceAndMocks.all(), utbetalingKlargjortForOversendelse.callback) {
            verify(serviceAndMocks.vedtakService).lagreITransaksjon(
                vedtak = any(),
                tx = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.revurderingRepo).lagre(
                revurdering = any(),
                transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(utbetalingKlargjortForOversendelse.callback).invoke(utbetalingsRequest)
        }
    }

    @Test
    fun `skal returnere left om revurdering ikke er av typen RevurderingTilAttestering`() {
        val (sak, iverksatt) = iverksattRevurdering()
        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = iverksatt.id,
            attestant = attestant,
        )

        response shouldBe KunneIkkeIverksetteRevurdering.Saksfeil.UgyldigTilstand(
            iverksattRevurdering().second::class,
            IverksattRevurdering::class,
        ).left()
    }

    @Test
    fun `skal returnere left dersom lagring feiler for innvilget`() {
        val clock = tikkendeFixedClock()
        val (sak, revurdering) = revurderingTilAttestering(
            clock = clock,
        )
        val simulertUtbetaling = simulerUtbetaling(
            sak = sak,
            revurdering = revurdering,
            clock = clock,
            behandler = revurdering.saksbehandler,
        ).getOrFail()

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = simulertUtbetaling.toOversendtUtbetaling(utbetalingsRequest),
            callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn UtbetalingFeilet.Protokollfeil.left()
            },
        )
        val expectedException = RuntimeException("Lagring feilet")

        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            vedtakService = mock {
                on { lagreITransaksjon(any(), anyOrNull()) } doThrow expectedException
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn simulerUtbetaling(
                    sak = sak,
                    revurdering = revurdering,
                    clock = clock,
                ).getOrFail().right()
                on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
            },
            clock = clock,
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurdering.id,
            attestant = attestant,
        )

        response shouldBe KunneIkkeIverksetteRevurdering.IverksettelsestransaksjonFeilet(
            KunneIkkeFerdigstilleIverksettelsestransaksjon.UkjentFeil(
                expectedException,
            ),
        ).left()
    }

    @Test
    fun `skal returnere left dersom lagring feiler for opphørt`() {
        val clock = tikkendeFixedClock()
        val (sak, revurdering) = revurderingTilAttestering(
            clock = clock,
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
        )

        val simulertUtbetaling = simulerOpphør(
            sak = sak,
            revurdering = revurdering,
            clock = clock,
        ).getOrFail()

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = simulertUtbetaling.toOversendtUtbetaling(utbetalingsRequest),
            callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn UtbetalingFeilet.Protokollfeil.left()
            },
        )

        val expectedException = RuntimeException("Lagring feilet")
        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            vedtakService = mock {
                on { lagreITransaksjon(any(), anyOrNull()) } doThrow expectedException
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn simulerOpphør(
                    sak = sak,
                    revurdering = revurdering,
                    clock = clock,
                ).getOrFail().right()
                on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
            },
            clock = clock,
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurdering.id,
            attestant = attestant,
        )

        response shouldBe KunneIkkeIverksetteRevurdering.IverksettelsestransaksjonFeilet(
            KunneIkkeFerdigstilleIverksettelsestransaksjon.UkjentFeil(
                expectedException,
            ),
        ).left()
    }

    @Test
    fun `skal returnere left dersom utbetaling feiler for opphørt`() {
        val clock = tikkendeFixedClock()
        val (sak, revurderingTilAttestering) = revurderingTilAttestering(
            clock = clock,
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
        ).let { (sak, revurdering) -> sak to revurdering as RevurderingTilAttestering.Opphørt }

        val simulertUtbetaling = simulerOpphør(
            sak = sak,
            revurdering = revurderingTilAttestering,
            clock = clock,
        ).getOrFail()

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = simulertUtbetaling.toOversendtUtbetaling(utbetalingsRequest),
            callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn UtbetalingFeilet.Protokollfeil.left()
            },
        )

        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn simulertUtbetaling.right()
                on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakService = mock {
                doNothing().whenever(it).lagreITransaksjon(any(), anyOrNull())
            },
            annullerKontrollsamtaleService = mock { },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            clock = clock,
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingTilAttestering.id,
            attestant = attestant,
        )

        response shouldBe KunneIkkeIverksetteRevurdering.IverksettelsestransaksjonFeilet(
            KunneIkkeFerdigstilleIverksettelsestransaksjon.KunneIkkeUtbetale(
                UtbetalingFeilet.Protokollfeil,
            ),
        ).left()
    }

    @Test
    fun `skal ikke opphøre dersom annullering av kontrollsamtale feiler`() {
        val clock = tikkendeFixedClock()
        val (sak, revurderingTilAttestering) = revurderingTilAttestering(
            clock = clock,
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag()),
        ).let { (sak, revurdering) -> sak to revurdering as RevurderingTilAttestering.Opphørt }

        val simulertUtbetaling = simulerOpphør(
            sak = sak,
            revurdering = revurderingTilAttestering,
            clock = clock,
        ).getOrFail()

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = simulertUtbetaling.toOversendtUtbetaling(utbetalingsRequest),
            callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn utbetalingsRequest.right()
            },
        )
        val expectedException =
            IllegalStateException("Emulerer at annullerKontrollsamtale(...) kastet en exception")
        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn simulertUtbetaling.right()
                on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakService = mock {
                doNothing().whenever(it).lagreITransaksjon(any(), anyOrNull())
            },
            annullerKontrollsamtaleService = mock {
                on {
                    annuller(
                        any(),
                        anyOrNull(),
                    )
                } doThrow expectedException
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            clock = clock,
        )

        serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingTilAttestering.id,
            attestant = attestant,
        ) shouldBe KunneIkkeIverksetteRevurdering.IverksettelsestransaksjonFeilet(
            KunneIkkeFerdigstilleIverksettelsestransaksjon.UkjentFeil(
                expectedException,
            ),
        ).left()
    }

    @Test
    fun `skal returnere left dersom utbetaling feiler for iverksett`() {
        val clock = tikkendeFixedClock()
        val (sak, revurderingTilAttestering) = revurderingTilAttestering(
            clock = clock,
        )

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = nyUtbetalingOversendUtenKvittering(
                sakOgBehandling = sak to revurderingTilAttestering,
                beregning = revurderingTilAttestering.beregning,
                clock = clock,
            ),
            callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn UtbetalingFeilet.Protokollfeil.left()
            },
        )

        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            revurderingRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                doAnswer { invocation ->
                    simulerUtbetaling(
                        utbetalingerPåSak = sak.utbetalinger,
                        utbetalingForSimulering = invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                    )
                }.whenever(it).simulerUtbetaling(any())
                on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakService = mock {
                doNothing().whenever(it).lagreITransaksjon(any(), anyOrNull())
            },
            annullerKontrollsamtaleService = mock {},
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            clock = clock,
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingTilAttestering.id,
            attestant = attestant,
        )
        response shouldBe KunneIkkeIverksetteRevurdering.IverksettelsestransaksjonFeilet(
            KunneIkkeFerdigstilleIverksettelsestransaksjon.KunneIkkeUtbetale(
                UtbetalingFeilet.Protokollfeil,
            ),
        ).left()
    }

    @Test
    fun `feil ved åpent kravgrunnlag`() {
        val clock = TikkendeKlokke()
        val (sak, vedtakAvventerKravgrunnlag) = vedtakRevurdering(
            clock = clock,
            revurderingsperiode = mai(2021)..desember(2021),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = mai(2021)..desember(2021),
                    arbeidsinntekt = 5000.0,
                ),
            ),
            utbetalingerKjørtTilOgMed = { 1.november(2021) },
        )

        val (sakMedTilAttestering, revurderingTilAttestering) = revurderingTilAttestering(
            clock = clock,
            sakOgVedtakSomKanRevurderes = sak to vedtakAvventerKravgrunnlag,
        )

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sakMedTilAttestering
            },
        ).also {
            it.revurderingService.iverksett(
                revurderingTilAttestering.id,
                attestant,
            ) shouldBe KunneIkkeIverksetteRevurdering.Saksfeil.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving
                .left()
        }
    }
}
