package no.nav.su.se.bakover.service.revurdering

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.kontrollsamtale.UgyldigStatusovergang
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingsinstruksjonForEtterbetalinger
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeTilbakekrev
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.kontrollsamtale.AnnulerKontrollsamtaleResultat
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataEnsligMedFradrag
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.nyUtbetalingOversendUtenKvittering
import no.nav.su.se.bakover.test.opphørUtbetalingOversendUtenKvittering
import no.nav.su.se.bakover.test.planlagtKontrollsamtale
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.utbetalingsRequest
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

internal class IverksettRevurderingTest {

    @Test
    fun `iverksett - iverksetter endring av ytelse`() {
        val sakOgVedtak = vedtakSøknadsbehandlingIverksattInnvilget()
        val grunnlagsdata = grunnlagsdataEnsligMedFradrag().let { it.fradragsgrunnlag + it.bosituasjon }

        val (sak, revurderingTilAttestering) = revurderingTilAttestering(
            sakOgVedtakSomKanRevurderes = sakOgVedtak,
            grunnlagsdataOverrides = grunnlagsdata,
        )

        val expected = (
            iverksattRevurdering(
                sakOgVedtakSomKanRevurderes = sakOgVedtak,
                grunnlagsdataOverrides = grunnlagsdata,
            ).second as IverksattRevurdering.Innvilget
            ).copy(
            id = revurderingTilAttestering.id,
            tilbakekrevingsbehandling = (revurderingTilAttestering as RevurderingTilAttestering.Innvilget).tilbakekrevingsbehandling.fullførBehandling(),
        )

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = nyUtbetalingOversendUtenKvittering(
                sakOgBehandling = sak to revurderingTilAttestering,
                beregning = revurderingTilAttestering.beregning,
                clock = fixedClock,
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
                on { klargjørNyUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        )

        val respone = serviceAndMocks.revurderingService.iverksett(revurderingTilAttestering.id, attestant)
            .getOrFail() as IverksattRevurdering.Innvilget

        respone shouldBe expected

        verify(serviceAndMocks.sakService).hentSakForRevurdering(argThat { it shouldBe revurderingTilAttestering.id })
        verify(serviceAndMocks.utbetalingService).klargjørNyUtbetaling(
            request = argThat {
                it shouldBe UtbetalRequest.NyUtbetaling(
                    request = SimulerUtbetalingRequest.NyUtbetaling.Uføre(
                        sakId = sakId,
                        saksbehandler = attestant,
                        beregning = revurderingTilAttestering.beregning,
                        uføregrunnlag = revurderingTilAttestering.vilkårsvurderinger.uføreVilkår().getOrFail().grunnlag,
                        utbetalingsinstruksjonForEtterbetaling = UtbetalingsinstruksjonForEtterbetalinger.SåFortSomMulig,
                    ),
                    simulering = revurderingTilAttestering.simulering,
                )
            },
            transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.vedtakRepo).lagre(
            vedtak = argThat { it should beOfType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering>() },
            sessionContext = argThat { TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.revurderingRepo).lagre(
            revurdering = argThat { it shouldBe expected },
            transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(utbetalingKlargjortForOversendelse.callback).invoke(utbetalingsRequest)
        verify(serviceAndMocks.tilbakekrevingService).hentAvventerKravgrunnlag(argThat<UUID> { it shouldBe expected.sakId })

        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `iverksett - iverksetter opphør av ytelse`() {
        val (sak, revurdering) = tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak()

        val utbetaling = opphørUtbetalingOversendUtenKvittering(
            sakOgBehandling = sak to revurdering,
            opphørsperiode = revurdering.periode,
            clock = fixedClock,
        )
        val callback =
            mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn utbetaling.utbetalingsrequest.right()
            }

        val utbetalingKlarForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = utbetaling,
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
                on { klargjørOpphør(any(), any(), any(), any(), any()) } doReturn utbetalingKlarForOversendelse.right()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            kontrollsamtaleService = mock {
                on {
                    annullerKontrollsamtale(
                        any(),
                        anyOrNull(),
                    )
                } doReturn AnnulerKontrollsamtaleResultat.AnnulertKontrollsamtale(planlagtKontrollsamtale()).right()
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        )

        serviceAndMocks.revurderingService.iverksett(revurdering.id, attestant)

        verify(serviceAndMocks.sakService).hentSakForRevurdering(revurdering.id)
        verify(serviceAndMocks.utbetalingService).klargjørOpphør(
            utbetaling = any(),
            eksisterendeUtbetalinger = argThat { it shouldBe sak.utbetalinger },
            opphørsperiode = argThat { it shouldBe revurdering.periode },
            saksbehandlersSimulering = argThat { it shouldBe revurdering.simulering },
            transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.vedtakRepo).lagre(
            vedtak = argThat { it should beOfType<VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering>() },
            sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.kontrollsamtaleService).annullerKontrollsamtale(
            sakId = argThat { it shouldBe revurdering.sakId },
            sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(serviceAndMocks.revurderingRepo).lagre(
            revurdering = any(),
            transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
        )
        verify(callback).invoke(utbetalingKlarForOversendelse.utbetaling.utbetalingsrequest)
        verify(serviceAndMocks.tilbakekrevingService).hentAvventerKravgrunnlag(argThat<UUID> { it shouldBe revurdering.sakId })
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `iverksett opphør - opphøret skal publiseres etter alle databasekallene`() {
        val (sak, revurderingTilAttestering) = tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak()

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = opphørUtbetalingOversendUtenKvittering(
                sakOgBehandling = sak to revurderingTilAttestering,
                opphørsperiode = revurderingTilAttestering.opphørsperiodeForUtbetalinger,
                clock = fixedClock,
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
                on {
                    klargjørOpphør(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            kontrollsamtaleService = mock {
                on {
                    annullerKontrollsamtale(
                        any(),
                        anyOrNull(),
                    )
                } doReturn AnnulerKontrollsamtaleResultat.AnnulertKontrollsamtale(
                    planlagtKontrollsamtale(),
                ).right()
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        )

        serviceAndMocks.revurderingService.iverksett(revurderingTilAttestering.id, attestant)

        inOrder(*serviceAndMocks.all(), utbetalingKlargjortForOversendelse.callback) {
            verify(serviceAndMocks.utbetalingService).klargjørOpphør(
                any(),
                any(),
                any(),
                any(),
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.vedtakRepo).lagre(
                any(),
                argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.kontrollsamtaleService).annullerKontrollsamtale(
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
        val (sak, revurderingTilAttestering) = revurderingTilAttestering()

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = nyUtbetalingOversendUtenKvittering(
                sakOgBehandling = sak to revurderingTilAttestering,
                beregning = revurderingTilAttestering.beregning,
                clock = fixedClock,
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
                on { klargjørNyUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        )

        serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingTilAttestering.id,
            attestant = attestant,
        )

        inOrder(*serviceAndMocks.all(), utbetalingKlargjortForOversendelse.callback) {
            verify(serviceAndMocks.vedtakRepo).lagre(
                vedtak = any(),
                sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
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
        val (sak, revurdering) = iverksattRevurdering()
        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurdering.id,
            attestant = attestant,
        )

        response shouldBe KunneIkkeIverksetteRevurdering.UgyldigTilstand(
            iverksattRevurdering().second::class,
            IverksattRevurdering::class,
        ).left()
    }

    @Test
    fun `skal returnere left dersom lagring feiler for innvilget`() {
        val (sak, revurdering) = revurderingTilAttestering()
        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            vedtakRepo = mock {
                on { lagre(any(), anyOrNull()) } doThrow RuntimeException("Lagring feilet")
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurdering.id,
            attestant = attestant,
        )

        response shouldBe KunneIkkeIverksetteRevurdering.LagringFeilet.left()
    }

    @Test
    fun `skal returnere left dersom lagring feiler for opphørt`() {
        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak().first
            },
            vedtakRepo = mock {
                on { lagre(any(), anyOrNull()) } doThrow RuntimeException("Lagring feilet")
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingId,
            attestant = attestant,
        )

        response shouldBe KunneIkkeIverksetteRevurdering.LagringFeilet.left()
    }

    @Test
    fun `skal returnere left dersom utbetaling feiler for opphørt`() {
        val (sak, revurderingTilAttestering) = tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak()
        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = opphørUtbetalingOversendUtenKvittering(
                sakOgBehandling = sak to revurderingTilAttestering,
                opphørsperiode = revurderingTilAttestering.opphørsperiodeForUtbetalinger,
                clock = fixedClock,
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
                on {
                    klargjørOpphør(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            kontrollsamtaleService = mock {
                on {
                    annullerKontrollsamtale(
                        any(),
                        anyOrNull(),
                    )
                } doReturn AnnulerKontrollsamtaleResultat.AnnulertKontrollsamtale(
                    planlagtKontrollsamtale(),
                ).right()
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingId,
            attestant = attestant,
        )

        response shouldBe KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(UtbetalingFeilet.Protokollfeil).left()
    }

    @Test
    fun `skal returnere left dersom lagring feiler for iverksett`() {
        val (sak, revurdering) = revurderingTilAttestering()
        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            vedtakRepo = mock {
                on { lagre(any(), anyOrNull()) } doThrow RuntimeException("Lagring feilet")
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurdering.id,
            attestant = attestant,
        )

        response shouldBe KunneIkkeIverksetteRevurdering.LagringFeilet.left()
    }

    @Test
    fun `skal ikke opphøre dersom annullering av kontrollsamtale feiler`() {
        val (sak, revurderingTilAttestering) = tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak()

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = opphørUtbetalingOversendUtenKvittering(
                sakOgBehandling = sak to revurderingTilAttestering,
                opphørsperiode = revurderingTilAttestering.opphørsperiodeForUtbetalinger,
                clock = fixedClock,
            ),
            callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                on { it.invoke(any()) } doReturn UtbetalingFeilet.Protokollfeil.left()
            },
        )
        val serviceAndMocks = RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            utbetalingService = mock {
                on {
                    klargjørOpphør(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                    )
                } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            kontrollsamtaleService = mock {
                on {
                    annullerKontrollsamtale(
                        any(),
                        anyOrNull(),
                    )
                } doReturn UgyldigStatusovergang.left()
                on { defaultSessionContext() } doReturn TestSessionFactory.sessionContext
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        )

        serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingId,
            attestant = attestant,
        ) shouldBe KunneIkkeIverksetteRevurdering.KunneIkkeAnnulereKontrollsamtale.left()
    }

    @Test
    fun `skal returnere left dersom utbetaling feiler for iverksett`() {
        val (sak, revurderingTilAttestering) = revurderingTilAttestering()

        val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
            utbetaling = nyUtbetalingOversendUtenKvittering(
                sakOgBehandling = sak to revurderingTilAttestering,
                beregning = revurderingTilAttestering.beregning,
                clock = fixedClock,
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
                doNothing().whenever(it).lagre(any(), any())
            },
            utbetalingService = mock {
                on { klargjørNyUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            kontrollsamtaleService = mock {
                on {
                    annullerKontrollsamtale(
                        any(),
                        anyOrNull(),
                    )
                } doReturn AnnulerKontrollsamtaleResultat.AnnulertKontrollsamtale(
                    planlagtKontrollsamtale(),
                ).right()
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingTilAttestering.id,
            attestant = attestant,
        )
        response shouldBe KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(UtbetalingFeilet.Protokollfeil).left()
    }

    @Test
    fun `feil ved åpent kravgrunnlag`() {
        val (sak, revurderingTilAttestering) = revurderingTilAttestering()

        RevurderingServiceMocks(
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn listOf(
                    IkkeTilbakekrev(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(),
                        sakId = revurderingTilAttestering.sakId,
                        revurderingId = revurderingTilAttestering.id,
                        periode = revurderingTilAttestering.periode,
                    ).fullførBehandling(),
                )
            },
        ).also {
            it.revurderingService.iverksett(
                revurderingTilAttestering.id,
                attestant,
            ) shouldBe KunneIkkeIverksetteRevurdering.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving.left()
        }
    }
}
