package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.beregning.KunneIkkeBeregneOgSimulereRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.revurdering.varsel.Varselmelding
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.bosituasjongrunnlagEnslig
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.revurderingUnderkjent
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.utbetaling.nyUtbetalingSimulert
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import vilkår.inntekt.domain.grunnlag.FradragTilhører
import økonomi.domain.simulering.SimuleringFeilet
import økonomi.domain.utbetaling.Utbetaling

internal class RevurderingBeregnOgSimulerTest {

    @Test
    fun `legger ved feilmeldinger for tilfeller som ikke støttes`() {
        val clock = TikkendeKlokke()
        val (sak, opprettet) = opprettetRevurdering(
            clock = clock,
            vilkårOverrides = listOf(
                avslåttUførevilkårUtenGrunnlag(),
            ),
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 150500.0),
            ),
        )

        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock(),
            utbetalingService = mock {
                doAnswer { invocation ->
                    simulerUtbetaling(
                        utbetalingerPåSak = sak.utbetalinger,
                        utbetalingForSimulering = invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                    )
                }.whenever(it).simulerUtbetaling(any())
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            clock = clock,
        )

        val response = serviceAndMocks.revurderingService.beregnOgSimuler(
            revurderingId = opprettet.id,
            saksbehandler = saksbehandler,
            skalUtsetteTilbakekreving = false,
        ).getOrFail()

        response.feilmeldinger shouldBe listOf(
            RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
        )
    }

    @Test
    fun `legger ved varsel dersom beløpsendring er mindre enn 10 prosent av gjeldende utbetaling`() {
        val clock = tikkendeFixedClock()
        val (sak, revurdering) = opprettetRevurdering(
            clock = clock,
        )

        RevurderingServiceMocks(
            revurderingRepo = mock(),
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn nyUtbetalingSimulert(
                    sakOgBehandling = sak to revurdering,
                    beregning = revurdering.beregn(
                        eksisterendeUtbetalinger = sak.utbetalinger,
                        clock = clock,
                        gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                            fraOgMed = revurdering.periode.fraOgMed,
                            clock = clock,
                        ).getOrFail(),
                        satsFactory = satsFactoryTestPåDato(),
                    ).getOrFail().beregning,
                    clock = clock,
                ).right()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            clock = clock,
        ).let {
            val response = it.revurderingService.beregnOgSimuler(
                revurderingId = revurdering.id,
                saksbehandler = saksbehandler,
                skalUtsetteTilbakekreving = false,
            ).getOrFail()

            response.revurdering shouldBe beOfType<SimulertRevurdering.Innvilget>()
            response.feilmeldinger shouldBe emptyList()
            response.varselmeldinger shouldBe listOf(Varselmelding.BeløpsendringUnder10Prosent)
        }
    }

    @Test
    fun `legger ikke ved varsel dersom beløpsendring er mindre enn 10 prosent av gjeldende utbetaling, men opphør pga vilkår`() {
        val clock = TikkendeKlokke()
        val (sak, revurdering) = opprettetRevurdering(
            clock = clock,
            vilkårOverrides = listOf(
                avslåttUførevilkårUtenGrunnlag(
                    opprettet = Tidspunkt.now(clock),
                    periode = år(2021),
                ),
            ),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock(),
            utbetalingService = mock {
                doAnswer { invocation ->
                    simulerUtbetaling(
                        utbetalingerPåSak = sak.utbetalinger,
                        utbetalingForSimulering = invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                        clock = clock,
                    )
                }.whenever(it).simulerUtbetaling(any())
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            clock = clock,
        ).let {
            val response = it.revurderingService.beregnOgSimuler(
                revurderingId = revurdering.id,
                saksbehandler = saksbehandler,
                skalUtsetteTilbakekreving = false,
            ).getOrFail()

            response.revurdering shouldBe beOfType<SimulertRevurdering.Opphørt>()
            response.feilmeldinger shouldBe emptyList()
            response.varselmeldinger shouldBe emptyList()
        }
    }

    @Test
    fun `beregnOgSimuler - kan beregne og simulere`() {
        val clock = TikkendeKlokke()
        val (sak, opprettetRevurdering) = opprettetRevurdering(
            clock = clock,
            grunnlagsdataOverrides = listOf(
                bosituasjongrunnlagEnslig(),
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock(),
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn nyUtbetalingSimulert(
                    sakOgBehandling = sak to opprettetRevurdering,
                    beregning = opprettetRevurdering.beregn(
                        eksisterendeUtbetalinger = sak.utbetalinger,
                        clock = clock,
                        gjeldendeVedtaksdata = sak.kopierGjeldendeVedtaksdata(
                            fraOgMed = opprettetRevurdering.periode.fraOgMed,
                            clock = clock,
                        ).getOrFail(),
                        satsFactory = satsFactoryTestPåDato(),
                    ).getOrFail().beregning,
                    clock = clock,
                ).right()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            clock = clock,
        )
        val actual = serviceAndMocks.revurderingService.beregnOgSimuler(
            revurderingId = opprettetRevurdering.id,
            saksbehandler = saksbehandler,
            skalUtsetteTilbakekreving = false,
        ).getOrFail()

        actual.let {
            it.revurdering shouldBe beOfType<SimulertRevurdering.Innvilget>()
            it.feilmeldinger shouldBe emptyList()
        }
        inOrder(
            *serviceAndMocks.all(),
        ) {
            verify(serviceAndMocks.sakService).hentSakForRevurdering(any())
            verify(serviceAndMocks.utbetalingService).simulerUtbetaling(
                utbetalingForSimulering = any(),
            )
            verify(serviceAndMocks.revurderingRepo).defaultTransactionContext()
            verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe actual.revurdering }, anyOrNull())
            verify(serviceAndMocks.sakService).hentSakForRevurdering(any())
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `beregnOgSimuler - kan ikke beregne og simulere en revurdering som er til attestering`() {
        val (sak, tilAttestring) = revurderingTilAttestering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock(),
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).let {
            val response = it.revurderingService.beregnOgSimuler(
                revurderingId = tilAttestring.id,
                saksbehandler = saksbehandler,
                skalUtsetteTilbakekreving = false,
            )
            response shouldBe KunneIkkeBeregneOgSimulereRevurdering.UgyldigTilstand(
                RevurderingTilAttestering.Innvilget::class,
                SimulertRevurdering::class,
            ).left()

            verify(it.sakService).hentSakForRevurdering(tilAttestring.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `beregnOgSimuler - får feil når simulering feiler`() {
        val (sak, beregnet) = beregnetRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 5000.0,
                    tilhører = FradragTilhører.BRUKER,
                ),
            ),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock(),
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn SimuleringFeilet.TekniskFeil.left()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).let {
            val response = it.revurderingService.beregnOgSimuler(
                revurderingId = beregnet.id,
                saksbehandler = saksbehandler,
                skalUtsetteTilbakekreving = true,
            )

            response shouldBe KunneIkkeBeregneOgSimulereRevurdering.KunneIkkeSimulere(
                SimuleringFeilet.TekniskFeil,
            )
                .left()

            verify(it.utbetalingService).simulerUtbetaling(
                utbetalingForSimulering = any(),
            )
        }
    }

    @Test
    fun `beregnOgSimuler - kan beregne og simuler underkjent revurdering på nytt`() {
        val clock = TikkendeKlokke()
        val (sak, underkjent) = revurderingUnderkjent(
            clock = clock,
        )

        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn underkjent
            },
            utbetalingService = mock {
                on { simulerUtbetaling(any()) } doReturn nyUtbetalingSimulert(
                    sakOgBehandling = sak to underkjent,
                    beregning = underkjent.beregning,
                    clock = clock,
                ).right()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            clock = clock,
        )

        val actual = serviceAndMocks.revurderingService.beregnOgSimuler(
            revurderingId = underkjent.id,
            saksbehandler = saksbehandler,
            skalUtsetteTilbakekreving = true,
        ).getOrFail()

        actual.let {
            it.revurdering shouldBe beOfType<SimulertRevurdering.Innvilget>()
            it.feilmeldinger shouldBe emptyList()
        }

        inOrder(
            *serviceAndMocks.all(),
        ) {
            verify(serviceAndMocks.sakService).hentSakForRevurdering(argThat { it shouldBe underkjent.id })
            verify(serviceAndMocks.utbetalingService).simulerUtbetaling(
                utbetalingForSimulering = any(),
            )
            verify(serviceAndMocks.revurderingRepo).defaultTransactionContext()
            verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe actual.revurdering }, anyOrNull())
            verify(serviceAndMocks.sakService).hentSakForRevurdering(argThat { it shouldBe underkjent.id })
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `hvis vilkår ikke er oppfylt, fører revurderingen til et opphør`() {
        val clock = TikkendeKlokke()
        val (sak, opprettet) = opprettetRevurdering(
            clock = clock,
            vilkårOverrides = listOf(
                avslåttUførevilkårUtenGrunnlag(opprettet = Tidspunkt.now(clock)),
            ),
        )

        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = mock(),
            utbetalingService = mock {
                doAnswer { invocation ->
                    simulerUtbetaling(
                        utbetalingerPåSak = sak.utbetalinger,
                        utbetalingForSimulering = invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                        clock = clock,
                    )
                }.whenever(it).simulerUtbetaling(any())
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            clock = clock,
        )
        val actual = serviceAndMocks.revurderingService.beregnOgSimuler(
            revurderingId = opprettet.id,
            saksbehandler = NavIdentBruker.Saksbehandler("s1"),
            skalUtsetteTilbakekreving = false,
        ).getOrFail().revurdering

        actual shouldBe beOfType<SimulertRevurdering.Opphørt>()

        inOrder(
            *serviceAndMocks.all(),
        ) {
            verify(serviceAndMocks.sakService).hentSakForRevurdering(opprettet.id)
            verify(serviceAndMocks.utbetalingService).simulerUtbetaling(
                argThat {
                    it.tidligsteDato() shouldBe opprettet.periode.fraOgMed
                    it.senesteDato() shouldBe opprettet.periode.tilOgMed
                    it.behandler shouldBe NavIdentBruker.Saksbehandler("s1")
                },
            )
            verify(serviceAndMocks.revurderingRepo).defaultTransactionContext()
            verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe actual }, anyOrNull())
            verify(serviceAndMocks.sakService).hentSakForRevurdering(any())
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }
}
