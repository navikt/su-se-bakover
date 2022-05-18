package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.domain.kontrollsamtale.UgyldigStatusovergang
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingskjøreplan
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.kontrollsamtale.AnnulerKontrollsamtaleResultat
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataEnsligMedFradrag
import no.nav.su.se.bakover.test.iverksattRevurdering
import no.nav.su.se.bakover.test.kontrollsamtale
import no.nav.su.se.bakover.test.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.simulertUtbetaling
import no.nav.su.se.bakover.test.simulertUtbetalingOpphør
import no.nav.su.se.bakover.test.tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
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

internal class IverksettRevurderingTest {

    @Test
    fun `iverksett - iverksetter endring av ytelse`() {
        val sakOgVedtak = vedtakSøknadsbehandlingIverksattInnvilget()
        val grunnlagsdata = grunnlagsdataEnsligMedFradrag().let { it.fradragsgrunnlag + it.bosituasjon }

        val revurderingTilAttestering = revurderingTilAttestering(
            sakOgVedtakSomKanRevurderes = sakOgVedtak,
            grunnlagsdataOverrides = grunnlagsdata,
        ).second as RevurderingTilAttestering.Innvilget

        val expected = (
            iverksattRevurdering(
                sakOgVedtakSomKanRevurderes = sakOgVedtak,
                grunnlagsdataOverrides = grunnlagsdata,
            ).second as IverksattRevurdering.Innvilget
            ).copy(tilbakekrevingsbehandling = revurderingTilAttestering.tilbakekrevingsbehandling.fullførBehandling())

        val simulertUtbetaling = simulertUtbetaling()

        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                on { verifiserOgSimulerUtbetaling(any()) } doReturn simulertUtbetaling.right()
                on { publiserUtbetaling(any()) } doReturn Utbetalingsrequest("<xml></xml>").right()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            }
        )

        val respone = serviceAndMocks.revurderingService.iverksett(revurderingTilAttestering.id, attestant).getOrFail() as IverksattRevurdering.Innvilget

        respone shouldBe expected

        verify(serviceAndMocks.revurderingRepo).hent(argThat { it shouldBe revurderingTilAttestering.id })
        verify(serviceAndMocks.utbetalingService).verifiserOgSimulerUtbetaling(
            argThat {
                it shouldBe UtbetalRequest.NyUtbetaling(
                    request = SimulerUtbetalingRequest.NyUtbetaling(
                        sakId = sakId,
                        saksbehandler = attestant,
                        beregning = revurderingTilAttestering.beregning,
                        uføregrunnlag = revurderingTilAttestering.vilkårsvurderinger.uføre.grunnlag,
                        kjøreplan = Utbetalingskjøreplan.NEI,
                    ),
                    simulering = revurderingTilAttestering.simulering,
                )
            },
        )
        verify(serviceAndMocks.utbetalingService).lagreUtbetaling(
            argThat { it shouldBe simulertUtbetaling },
            anyOrNull(),
        )
        verify(serviceAndMocks.utbetalingService).publiserUtbetaling(argThat { it shouldBe simulertUtbetaling })
        verify(serviceAndMocks.vedtakRepo).lagre(
            argThat { it should beOfType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetRevurdering>() },
            anyOrNull(),
        )
        verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe expected }, anyOrNull())

        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `iverksett - iverksetter opphør av ytelse`() {
        val sakOgRevurdering = tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak()
        val revurderingTilAttestering = sakOgRevurdering.second
        val simulertOpphør =
            simulertUtbetalingOpphør(eksisterendeUtbetalinger = sakOgRevurdering.first.utbetalinger).getOrFail()
        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                on { verifiserOgSimulerOpphør(any()) } doReturn simulertOpphør.right()
                on { publiserUtbetaling(any()) } doReturn Utbetalingsrequest("<xml></xml>").right()
                on { lagreUtbetaling(any(), anyOrNull()) } doReturn oversendtUtbetalingUtenKvittering()
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
                    kontrollsamtale(),
                ).right()
            },
        )

        serviceAndMocks.revurderingService.iverksett(revurderingTilAttestering.id, attestant)

        verify(serviceAndMocks.revurderingRepo).hent(revurderingTilAttestering.id)
        verify(serviceAndMocks.utbetalingService).verifiserOgSimulerOpphør(
            argThat {
                it shouldBe UtbetalRequest.Opphør(
                    request = SimulerUtbetalingRequest.Opphør(
                        sakId = sakId,
                        saksbehandler = attestant,
                        opphørsdato = revurderingTilAttestering.periode.fraOgMed,
                    ),
                    simulering = revurderingTilAttestering.simulering,
                )
            },
        )
        verify(serviceAndMocks.vedtakRepo).lagre(
            argThat { it should beOfType<VedtakSomKanRevurderes.EndringIYtelse.OpphørtRevurdering>() },
            anyOrNull(),
        )
        verify(serviceAndMocks.kontrollsamtaleService).annullerKontrollsamtale(
            argThat { it shouldBe revurderingTilAttestering.sakId },
            anyOrNull(),
        )
        verify(serviceAndMocks.revurderingRepo).lagre(any(), anyOrNull())
        verify(serviceAndMocks.utbetalingService).lagreUtbetaling(argThat { it shouldBe simulertOpphør }, anyOrNull())
        verify(serviceAndMocks.utbetalingService).publiserUtbetaling(simulertOpphør)
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `iverksett opphør - opphøret skal publiseres etter alle databasekallene`() {
        val revurderingTilAttestering =
            tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak().second
        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                on { verifiserOgSimulerOpphør(any()) } doReturn simulertUtbetaling().right()
                on { publiserUtbetaling(any()) } doReturn Utbetalingsrequest("<xml></xml>").right()
                on { lagreUtbetaling(any(), anyOrNull()) } doReturn oversendtUtbetalingUtenKvittering()
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
                    kontrollsamtale(),
                ).right()
            },
        )

        serviceAndMocks.revurderingService.iverksett(revurderingTilAttestering.id, attestant)

        inOrder(*serviceAndMocks.all()) {
            verify(serviceAndMocks.utbetalingService).lagreUtbetaling(any(), anyOrNull())
            verify(serviceAndMocks.vedtakRepo).lagre(any(), anyOrNull())
            verify(serviceAndMocks.kontrollsamtaleService).annullerKontrollsamtale(any(), anyOrNull())
            verify(serviceAndMocks.revurderingRepo).lagre(any(), anyOrNull())
            verify(serviceAndMocks.utbetalingService).publiserUtbetaling(any())
        }
    }

    @Test
    fun `iverksett innvilget - utbetaling skal publiseres etter alle databasekallene`() {
        val revurderingTilAttestering = revurderingTilAttestering().second

        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                on { verifiserOgSimulerUtbetaling(any()) } doReturn simulertUtbetaling().right()
                on { publiserUtbetaling(any()) } doReturn Utbetalingsrequest("<xml></xml>").right()
            },
            vedtakRepo = mock {
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
        )

        serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingTilAttestering.id,
            attestant = attestant,
        )

        inOrder(*serviceAndMocks.all()) {
            verify(serviceAndMocks.utbetalingService).lagreUtbetaling(any(), anyOrNull())
            verify(serviceAndMocks.vedtakRepo).lagre(any(), anyOrNull())
            verify(serviceAndMocks.revurderingRepo).lagre(any(), anyOrNull())
            verify(serviceAndMocks.utbetalingService).publiserUtbetaling(any())
        }
    }

    @Test
    fun `skal returnere left om revurdering ikke er av typen RevurderingTilAttestering`() {
        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn iverksattRevurdering().second
            },
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingId,
            attestant = attestant,
        )

        response shouldBe KunneIkkeIverksetteRevurdering.UgyldigTilstand(
            iverksattRevurdering().second::class,
            IverksattRevurdering::class,
        ).left()
    }

    @Test
    fun `skal returnere left dersom lagring feiler for innvilget`() {
        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering().second
            },
            utbetalingService = mock {
                on { verifiserOgSimulerUtbetaling(any()) } doReturn simulertUtbetaling().right()
            },
            vedtakRepo = mock {
                on { lagre(any(), anyOrNull()) } doThrow RuntimeException("Lagring feilet")
            },
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingId,
            attestant = attestant,
        )

        response shouldBe KunneIkkeIverksetteRevurdering.LagringFeilet.left()
    }

    @Test
    fun `skal returnere left dersom lagring feiler for opphørt`() {
        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak().second
            },
            utbetalingService = mock {
                on { verifiserOgSimulerOpphør(any()) } doReturn simulertUtbetaling().right()
            },
            vedtakRepo = mock {
                on { lagre(any(), anyOrNull()) } doThrow RuntimeException("Lagring feilet")
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
        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak().second
                doNothing().whenever(it).lagre(any(), anyOrNull())
            },
            utbetalingService = mock {
                on { verifiserOgSimulerOpphør(any()) } doReturn simulertUtbetaling().right()
                on { publiserUtbetaling(any()) } doReturn UtbetalingFeilet.FantIkkeSak.left()
                on { lagreUtbetaling(any(), anyOrNull()) } doReturn oversendtUtbetalingUtenKvittering()
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
                    kontrollsamtale(),
                ).right()
            },
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingId,
            attestant = attestant,
        )

        response shouldBe KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(UtbetalingFeilet.FantIkkeSak).left()
    }

    @Test
    fun `skal returnere left dersom lagring feiler for iverksett`() {
        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering().second
            },
            utbetalingService = mock {
                on { verifiserOgSimulerUtbetaling(any()) } doReturn simulertUtbetaling().right()
            },
            vedtakRepo = mock {
                on { lagre(any(), anyOrNull()) } doThrow RuntimeException("Lagring feilet")
            },
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingId,
            attestant = attestant,
        )

        response shouldBe KunneIkkeIverksetteRevurdering.LagringFeilet.left()
    }

    @Test
    fun `skal ikke opphøre dersom annulering av kontrollsamtale feiler`() {
        val sakOgRevurdering = tilAttesteringRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak()
        val revurderingTilAttestering = sakOgRevurdering.second
        val simulertOpphør = simulertUtbetalingOpphør(eksisterendeUtbetalinger = sakOgRevurdering.first.utbetalinger).getOrFail()
        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering
            },
            utbetalingService = mock {
                on { verifiserOgSimulerOpphør(any()) } doReturn simulertOpphør.right()
                on { verifiserOgSimulerUtbetaling(any()) } doReturn simulertUtbetaling().right()
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
        )

        serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingId,
            attestant = attestant,
        ) shouldBe KunneIkkeIverksetteRevurdering.KunneIkkeAnnulereKontrollsamtale.left()
    }

    @Test
    fun `skal returnere left dersom utbetaling feiler for iverksett`() {
        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering().second
            },
            utbetalingService = mock {
                on { verifiserOgSimulerUtbetaling(any()) } doReturn simulertUtbetaling().right()
                on { publiserUtbetaling(any()) } doReturn UtbetalingFeilet.FantIkkeSak.left()
                on { lagreUtbetaling(any(), anyOrNull()) } doReturn oversendtUtbetalingUtenKvittering()
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
                    kontrollsamtale(),
                ).right()
            },
        )

        val response = serviceAndMocks.revurderingService.iverksett(
            revurderingId = revurderingId,
            attestant = attestant,
        )
        response shouldBe KunneIkkeIverksetteRevurdering.KunneIkkeUtbetale(UtbetalingFeilet.FantIkkeSak).left()
    }
}
