package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.Fnr
import no.nav.su.se.bakover.common.NavIdentBruker
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeTilbakekrev
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeIverksette
import no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.kontrollsamtale.OpprettPlanlagtKontrollsamtaleResultat
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.attesteringIverksatt
import no.nav.su.se.bakover.test.avkortingsvarselUtenlandsopphold
import no.nav.su.se.bakover.test.dokumentUtenMetadataVedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySakUføre
import no.nav.su.se.bakover.test.nyUtbetalingOversendUtenKvittering
import no.nav.su.se.bakover.test.planlagtKontrollsamtale
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulerUtbetaling
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.utbetalingsRequest
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

internal class SøknadsbehandlingServiceIverksettTest {

    @Nested
    inner class GenerelleFeil {
        @Test
        fun `svarer med feil dersom vi ikke finner behandling`() {
            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn nySakUføre().first
                },
            )

            val response = serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    UUID.randomUUID(),
                    Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            )

            response shouldBe KunneIkkeIverksette.FantIkkeBehandling.left()
        }

        @Test
        fun `kaster exception ved ugyldig statusovergang`() {
            val (sak, behandling) = søknadsbehandlingVilkårsvurdertInnvilget()

            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
            )

            assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
                serviceAndMocks.søknadsbehandlingService.iverksett(
                    SøknadsbehandlingService.IverksettRequest(
                        behandlingId = behandling.id,
                        attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                    ),
                )
            }
        }

        @Test
        fun `feiler hvis utestående avkortinger ikke kunne avkortes fullstendig`() {
            val (sak, vilkårsvurdert) = søknadsbehandlingVilkårsvurdertInnvilget()

            val attestert = vilkårsvurdert.leggTilFradragsgrunnlag(
                fradragsgrunnlag = listOf(fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 20000.0)),
            ).getOrFail().beregn(
                begrunnelse = null,
                clock = fixedClock,
                satsFactory = satsFactoryTestPåDato(),
            ).getOrFail().let { beregnet ->
                beregnet.simuler(
                    saksbehandler = saksbehandler,
                ) { _, _ ->
                    simulerUtbetaling(
                        sak = sak,
                        søknadsbehandling = beregnet,
                    ).map {
                        it.simulering
                    }
                }
            }.getOrFail().tilAttestering(
                saksbehandler = saksbehandler,
                fritekstTilBrev = "njet",
            )

            val avkortingsvarsel = avkortingsvarselUtenlandsopphold().skalAvkortes()

            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak.copy(
                        søknadsbehandlinger = sak.søknadsbehandlinger.filterNot { it.id == attestert.id } + attestert.copy(
                            avkorting = AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
                                avkortingsvarsel,
                            ),
                        ),
                    )
                },
                avkortingsvarselRepo = mock {
                    on { hent(any()) } doReturn avkortingsvarsel
                },
                tilbakekrevingService = mock {
                    on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
                },
            )
            val response = serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    vilkårsvurdert.id,
                    Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            )
            response shouldBe KunneIkkeIverksette.AvkortingErUfullstendig.left()
        }

        @Test
        fun `svarer med feil dersom vi ikke kunne simulere`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()
            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
                utbetalingService = mock {
                    on { simulerUtbetaling(any(), any()) } doReturn SimuleringFeilet.TekniskFeil.left()
                },
                tilbakekrevingService = mock {
                    on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
                },
            )

            val response = serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    innvilgetTilAttestering.id,
                    Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            )

            response shouldBe KunneIkkeIverksette.KunneIkkeUtbetale(UtbetalingFeilet.KunneIkkeSimulere(SimulerUtbetalingFeilet.FeilVedSimulering(SimuleringFeilet.TekniskFeil))).left()
        }

        @Test
        fun `svarer med feil dersom kontrollsimulering var for ulik`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget(
                clock = tikkendeFixedClock,
            )
            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
                utbetalingService = mock {
                    doAnswer { invocation ->
                        simulerUtbetaling(
                            sak,
                            (invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering).copy(fnr = Fnr.generer()),
                            invocation.getArgument(1) as Periode,
                            tikkendeFixedClock,
                        )
                    }.whenever(it).simulerUtbetaling(any(), any())
                },
                tilbakekrevingService = mock {
                    on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
                },
            )

            val response = serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = innvilgetTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            )

            response shouldBe KunneIkkeIverksette.KunneIkkeUtbetale(
                UtbetalingFeilet.KunneIkkeSimulere(SimulerUtbetalingFeilet.FeilVedKryssjekkAvSaksbehandlerOgAttestantsSimulering(KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikGjelderId)),
            ).left()
        }

        @Test
        fun `attestant og saksbehandler kan ikke være samme person`() {
            val (sak, avslagTilAttestering) = søknadsbehandlingTilAttesteringAvslagMedBeregning()
            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
            )

            val response = serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = avslagTilAttestering.id,
                    attestering = Attestering.Iverksatt(
                        NavIdentBruker.Attestant(avslagTilAttestering.saksbehandler.navIdent),
                        fixedTidspunkt,
                    ),
                ),
            )

            response shouldBe KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }
    }

    @Nested
    inner class Avslag {
        @Test
        fun `svarer med feil dersom generering av vedtaksbrev feiler`() {
            val (sak, avslagTilAttestering) = søknadsbehandlingTilAttesteringAvslagMedBeregning()
            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
                søknadsbehandlingRepo = mock {
                    on { hent(any()) } doReturn avslagTilAttestering
                },
                brevService = mock {
                    on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn KunneIkkeLageDokument.KunneIkkeHentePerson.left()
                },
            )
            val response = serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = avslagTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            )
            response shouldBe KunneIkkeIverksette.KunneIkkeGenerereVedtaksbrev.left()
        }

        @Test
        fun `attesterer og iverksetter avslag hvis alt er ok`() {
            val attestering = Attestering.Iverksatt(attestant, fixedTidspunkt)
            val (sak, avslagTilAttestering) = søknadsbehandlingTilAttesteringAvslagMedBeregning()
            val expectedAvslag = avslagTilAttestering.tilIverksatt(attestering)

            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
                søknadsbehandlingRepo = mock {
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                ferdigstillVedtakService = mock { mock ->
                    doAnswer {
                        (it.arguments[0]).right()
                    }.whenever(mock).lukkOppgaveMedBruker(any())
                },
                brevService = mock {
                    on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn dokumentUtenMetadataVedtak().right()
                },
                vedtakRepo = mock {
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
            )

            serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = avslagTilAttestering.id,
                    attestering = attestering,
                ),
            ) shouldBe expectedAvslag.right()

            verify(serviceAndMocks.sakService).hentSakForSøknadsbehandling(avslagTilAttestering.id)
            verify(serviceAndMocks.brevService).lagDokument(argThat<Visitable<LagBrevRequestVisitor>> { it shouldBe beOfType<Avslagsvedtak.AvslagBeregning>() })
            verify(serviceAndMocks.søknadsbehandlingRepo).lagre(eq(expectedAvslag), anyOrNull())
            verify(serviceAndMocks.vedtakRepo).lagre(argThat { it is Avslagsvedtak.AvslagBeregning }, anyOrNull())
            verify(serviceAndMocks.brevService).lagreDokument(
                argThat {
                    it.metadata.sakId shouldBe avslagTilAttestering.sakId
                    it.metadata.vedtakId shouldNotBe null
                    it.metadata.bestillBrev shouldBe true
                },
                anyOrNull(),
            )
            verify(serviceAndMocks.behandlingMetrics).incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.PERSISTERT)
            verify(serviceAndMocks.ferdigstillVedtakService).lukkOppgaveMedBruker(any())
            verify(serviceAndMocks.observer).handle(
                argThat {
                    it shouldBe StatistikkEvent.Behandling.Søknad.Iverksatt.Avslag(
                        vedtak = Avslagsvedtak.fromSøknadsbehandlingMedBeregning(
                            avslag = expectedAvslag,
                            clock = fixedClock,
                        ).copy(
                            id = (it as StatistikkEvent.Behandling.Søknad.Iverksatt.Avslag).vedtak.id,
                        ),
                    )
                },
            )
            serviceAndMocks.verifyNoMoreInteractions()
        }

        @Test
        fun `avslår ikke dersom vi ikke kan lagre søknadsbehandlinga`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringAvslagMedBeregning()
            val utbetalingMedCallback = mock<UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>()
            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
                søknadsbehandlingRepo = mock {
                    doThrow(RuntimeException()).whenever(it).lagre(any(), anyOrNull())
                },
                utbetalingService = mock {
                    on { klargjørUtbetaling(any(), any()) } doReturn utbetalingMedCallback.right()
                },
                kontrollsamtaleService = mock {
                    on {
                        opprettPlanlagtKontrollsamtale(
                            any(),
                            any(),
                        )
                    } doReturn OpprettPlanlagtKontrollsamtaleResultat.Opprettet(planlagtKontrollsamtale())
                },
                vedtakRepo = mock {
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                brevService = mock {
                    on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn dokumentUtenMetadataVedtak().right()
                },
            )

            serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = innvilgetTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            ) shouldBe KunneIkkeIverksette.LagringFeilet.left()

            verify(utbetalingMedCallback, never()).sendUtbetaling()
        }

        @Test
        fun `avslår ikke dersom vi ikke kan lagre vedtaket`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringAvslagMedBeregning()
            val utbetalingMedCallback = mock<UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>()
            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
                søknadsbehandlingRepo = mock {
                    on { hent(any()) } doReturn innvilgetTilAttestering
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                utbetalingService = mock {
                    on { klargjørUtbetaling(any(), any()) } doReturn utbetalingMedCallback.right()
                },
                kontrollsamtaleService = mock {
                    on {
                        opprettPlanlagtKontrollsamtale(
                            any(),
                            any(),
                        )
                    } doReturn OpprettPlanlagtKontrollsamtaleResultat.Opprettet(planlagtKontrollsamtale())
                },
                vedtakRepo = mock {
                    doThrow(RuntimeException()).whenever(it).lagre(any(), anyOrNull())
                },
                brevService = mock {
                    on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn dokumentUtenMetadataVedtak().right()
                },
            )

            serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = innvilgetTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            ) shouldBe KunneIkkeIverksette.LagringFeilet.left()

            verify(utbetalingMedCallback, never()).sendUtbetaling()
        }

        @Test
        fun `avslår ikke dersom vi ikke kan lagre brevet`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringAvslagMedBeregning()
            val utbetalingMedCallback = mock<UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>()
            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
                søknadsbehandlingRepo = mock {
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                utbetalingService = mock {
                    on { klargjørUtbetaling(any(), any()) } doReturn utbetalingMedCallback.right()
                },
                kontrollsamtaleService = mock {
                    on {
                        opprettPlanlagtKontrollsamtale(
                            any(),
                            any(),
                        )
                    } doReturn OpprettPlanlagtKontrollsamtaleResultat.Opprettet(planlagtKontrollsamtale())
                },
                vedtakRepo = mock {
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                brevService = mock {
                    on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn dokumentUtenMetadataVedtak().right()
                    doThrow(RuntimeException()).whenever(it).lagreDokument(any(), anyOrNull())
                },
            )

            serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = innvilgetTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            ) shouldBe KunneIkkeIverksette.LagringFeilet.left()

            verify(utbetalingMedCallback, never()).sendUtbetaling()
        }
    }

    @Nested
    inner class Innvilgelse {

        @Test
        fun `svarer med feil dersom vi ikke kunne utbetale`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()

            val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
                utbetaling = nyUtbetalingOversendUtenKvittering(
                    sakOgBehandling = sak to innvilgetTilAttestering,
                    beregning = innvilgetTilAttestering.beregning,
                    clock = fixedClock,
                ),
                callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                    on { it.invoke(any()) } doReturn UtbetalingFeilet.Protokollfeil.left()
                },
            )

            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
                søknadsbehandlingRepo = mock {
                    on { hent(any()) } doReturn innvilgetTilAttestering
                },
                utbetalingService = mock {
                    doAnswer { invocation ->
                        simulerUtbetaling(
                            sak,
                            invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                            invocation.getArgument(1) as Periode,
                            tikkendeFixedClock,
                        )
                    }.whenever(it).simulerUtbetaling(any(), any())
                    on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
                },
                vedtakRepo = mock {
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                kontrollsamtaleService = mock {
                    on {
                        opprettPlanlagtKontrollsamtale(
                            any(),
                            any(),
                        )
                    } doReturn OpprettPlanlagtKontrollsamtaleResultat.Opprettet(planlagtKontrollsamtale())
                },
                tilbakekrevingService = mock {
                    on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
                },
            )
            serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = innvilgetTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            ) shouldBe KunneIkkeIverksette.KunneIkkeUtbetale(utbetalingFeilet = UtbetalingFeilet.Protokollfeil).left()
        }

        @Test
        fun `attesterer og iverksetter innvilgning hvis alt er ok`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()

            val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
                utbetaling = nyUtbetalingOversendUtenKvittering(
                    sakOgBehandling = sak to innvilgetTilAttestering,
                    beregning = innvilgetTilAttestering.beregning,
                    clock = fixedClock,
                ),
                callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                    on { it.invoke(any()) } doReturn utbetalingsRequest.right()
                },
            )

            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
                søknadsbehandlingRepo = mock {
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                utbetalingService = mock {
                    doAnswer { invocation ->
                        simulerUtbetaling(
                            sak,
                            invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                            invocation.getArgument(1) as Periode,
                            tikkendeFixedClock,
                        )
                    }.whenever(it).simulerUtbetaling(any(), any())
                    on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
                },
                kontrollsamtaleService = mock {
                    on {
                        opprettPlanlagtKontrollsamtale(
                            any(),
                            any(),
                        )
                    } doReturn OpprettPlanlagtKontrollsamtaleResultat.Opprettet(planlagtKontrollsamtale())
                },
                vedtakRepo = mock {
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                tilbakekrevingService = mock {
                    on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
                },
            )

            val actual = serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = innvilgetTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            ).getOrFail() as Søknadsbehandling.Iverksatt.Innvilget

            val expected = Søknadsbehandling.Iverksatt.Innvilget(
                id = actual.id,
                opprettet = actual.opprettet,
                sakId = actual.sakId,
                saksnummer = actual.saksnummer,
                søknad = actual.søknad,
                oppgaveId = actual.oppgaveId,
                fnr = actual.fnr,
                beregning = actual.beregning,
                simulering = actual.simulering,
                saksbehandler = actual.saksbehandler,
                attesteringer = Attesteringshistorikk.empty()
                    .leggTilNyAttestering(Attestering.Iverksatt(attestant, fixedTidspunkt)),
                fritekstTilBrev = actual.fritekstTilBrev,
                stønadsperiode = actual.stønadsperiode,
                grunnlagsdata = actual.grunnlagsdata,
                vilkårsvurderinger = actual.vilkårsvurderinger,
                avkorting = actual.avkorting,
                sakstype = actual.sakstype,
            )

            actual shouldBe expected

            verify(serviceAndMocks.sakService).hentSakForSøknadsbehandling(innvilgetTilAttestering.id)
            verify(serviceAndMocks.utbetalingService, times(2)).simulerUtbetaling(
                any(),
                any(),
            )
            verify(serviceAndMocks.utbetalingService).klargjørUtbetaling(
                utbetaling = any(),
                transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.søknadsbehandlingRepo).lagre(
                søknadsbehandling = argThat { it shouldBe expected },
                sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.vedtakRepo).lagre(
                vedtak = argThat {
                    it shouldBe vedtakSøknadsbehandlingIverksattInnvilget().second.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        behandling = expected,
                        utbetalingId = utbetalingKlargjortForOversendelse.utbetaling.id,
                    )
                },
                sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.kontrollsamtaleService).opprettPlanlagtKontrollsamtale(
                vedtak = argThat {
                    it shouldBe vedtakSøknadsbehandlingIverksattInnvilget().second.copy(
                        id = it.id,
                        opprettet = it.opprettet,
                        behandling = expected,
                        utbetalingId = utbetalingKlargjortForOversendelse.utbetaling.id,
                    )
                },
                sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(utbetalingKlargjortForOversendelse.callback).invoke(utbetalingsRequest)
            verify(serviceAndMocks.behandlingMetrics).incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)
            verify(serviceAndMocks.observer, times(2)).handle(any())
            verify(serviceAndMocks.tilbakekrevingService).hentAvventerKravgrunnlag(argThat<UUID> { it shouldBe expected.sakId })
            serviceAndMocks.verifyNoMoreInteractions()
        }

        @Test
        fun `verifiser at utbetaling skjer etter alle lagre-kall`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()

            val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
                utbetaling = nyUtbetalingOversendUtenKvittering(
                    sakOgBehandling = sak to innvilgetTilAttestering,
                    beregning = innvilgetTilAttestering.beregning,
                    clock = fixedClock,
                ),
                callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                    on { it.invoke(any()) } doReturn utbetalingsRequest.right()
                },
            )

            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
                søknadsbehandlingRepo = mock {
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                utbetalingService = mock {
                    doAnswer { invocation ->
                        simulerUtbetaling(
                            sak,
                            invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                            invocation.getArgument(1) as Periode,
                            tikkendeFixedClock,
                        )
                    }.whenever(it).simulerUtbetaling(any(), any())
                    on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
                },
                kontrollsamtaleService = mock {
                    on {
                        opprettPlanlagtKontrollsamtale(
                            any(),
                            any(),
                        )
                    } doReturn OpprettPlanlagtKontrollsamtaleResultat.Opprettet(planlagtKontrollsamtale())
                },
                vedtakRepo = mock {
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                tilbakekrevingService = mock {
                    on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
                },
            )

            serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = innvilgetTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            )

            inOrder(*serviceAndMocks.allMocks(), utbetalingKlargjortForOversendelse.callback) {
                verify(serviceAndMocks.søknadsbehandlingRepo).lagre(
                    søknadsbehandling = any(),
                    sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
                )
                verify(serviceAndMocks.vedtakRepo).lagre(
                    vedtak = any(),
                    sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
                )
                verify(serviceAndMocks.kontrollsamtaleService).opprettPlanlagtKontrollsamtale(
                    vedtak = any(),
                    sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
                )
                verify(utbetalingKlargjortForOversendelse.callback).invoke(utbetalingsRequest)
            }
        }

        @Test
        fun `utbetaler selvom det allerede finnes en planlagt kontrollsamtale`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()

            val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
                utbetaling = nyUtbetalingOversendUtenKvittering(
                    sakOgBehandling = sak to innvilgetTilAttestering,
                    beregning = innvilgetTilAttestering.beregning,
                    clock = fixedClock,
                ),
                callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                    on { it.invoke(any()) } doReturn utbetalingsRequest.right()
                },
            )

            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
                søknadsbehandlingRepo = mock {
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                utbetalingService = mock {
                    doAnswer { invocation ->
                        simulerUtbetaling(
                            sak,
                            invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                            invocation.getArgument(1) as Periode,
                            tikkendeFixedClock,
                        )
                    }.whenever(it).simulerUtbetaling(any(), any())
                    on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
                },
                kontrollsamtaleService = mock {
                    on {
                        opprettPlanlagtKontrollsamtale(
                            any(),
                            any(),
                        )
                    } doReturn OpprettPlanlagtKontrollsamtaleResultat.PlanlagtKontrollsamtaleFinnesAllerede(
                        planlagtKontrollsamtale(),
                    )
                },
                vedtakRepo = mock {
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                tilbakekrevingService = mock {
                    on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
                },
            )

            serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = innvilgetTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            ).shouldBeRight()

            verify(utbetalingKlargjortForOversendelse.callback).invoke(utbetalingsRequest)
        }

        @Test
        fun `utbetaler selvom vi ikke skal planlegge en kontrollsamtale`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()

            val utbetalingKlargjortForOversendelse = UtbetalingKlargjortForOversendelse(
                utbetaling = nyUtbetalingOversendUtenKvittering(
                    sakOgBehandling = sak to innvilgetTilAttestering,
                    beregning = innvilgetTilAttestering.beregning,
                    clock = fixedClock,
                ),
                callback = mock<(utbetalingsrequest: Utbetalingsrequest) -> Either<UtbetalingFeilet.Protokollfeil, Utbetalingsrequest>> {
                    on { it.invoke(any()) } doReturn utbetalingsRequest.right()
                },
            )

            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
                søknadsbehandlingRepo = mock {
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                utbetalingService = mock {
                    doAnswer { invocation ->
                        simulerUtbetaling(
                            sak,
                            invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                            invocation.getArgument(1) as Periode,
                            tikkendeFixedClock,
                        )
                    }.whenever(it).simulerUtbetaling(any(), any())
                    on { klargjørUtbetaling(any(), any()) } doReturn utbetalingKlargjortForOversendelse.right()
                },
                kontrollsamtaleService = mock {
                    on {
                        opprettPlanlagtKontrollsamtale(
                            any(),
                            any(),
                        )
                    } doReturn OpprettPlanlagtKontrollsamtaleResultat.SkalIkkePlanleggeKontrollsamtale
                },
                vedtakRepo = mock {
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                tilbakekrevingService = mock {
                    on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
                },
            )

            serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = innvilgetTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            ).shouldBeRight()

            verify(utbetalingKlargjortForOversendelse.callback).invoke(utbetalingsRequest)
        }

        @Test
        fun `utbetaler ikke dersom vi ikke kan lagre behandlinga`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()
            val utbetalingMedCallback = mock<UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>()
            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
                søknadsbehandlingRepo = mock {
                    doThrow(RuntimeException()).whenever(it).lagre(any(), anyOrNull())
                },
                utbetalingService = mock {
                    on { klargjørUtbetaling(any(), any()) } doReturn utbetalingMedCallback.right()
                },
                kontrollsamtaleService = mock {
                    on {
                        opprettPlanlagtKontrollsamtale(
                            any(),
                            any(),
                        )
                    } doReturn OpprettPlanlagtKontrollsamtaleResultat.Opprettet(planlagtKontrollsamtale())
                },
                vedtakRepo = mock {
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                tilbakekrevingService = mock {
                    on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
                },
            )

            serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = innvilgetTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            ) shouldBe KunneIkkeIverksette.LagringFeilet.left()

            verify(utbetalingMedCallback, never()).sendUtbetaling()
        }

        @Test
        fun `utbetaler ikke dersom vi ikke kan lagre utbetalinga`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()
            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
                søknadsbehandlingRepo = mock {
                    on { hent(any()) } doReturn innvilgetTilAttestering
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                utbetalingService = mock {
                    on { klargjørUtbetaling(any(), any()) } doThrow RuntimeException()
                },
                kontrollsamtaleService = mock {
                    on {
                        opprettPlanlagtKontrollsamtale(
                            any(),
                            any(),
                        )
                    } doReturn OpprettPlanlagtKontrollsamtaleResultat.Opprettet(planlagtKontrollsamtale())
                },
                vedtakRepo = mock {
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                tilbakekrevingService = mock {
                    on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
                },
            )

            serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = innvilgetTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            ) shouldBe KunneIkkeIverksette.LagringFeilet.left()
        }

        @Test
        fun `utbetaler ikke dersom vi ikke kan lagre vedtaket`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()
            val utbetalingMedCallback = mock<UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>()
            val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
                søknadsbehandlingRepo = mock {
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                utbetalingService = mock {
                    on { klargjørUtbetaling(any(), any()) } doReturn utbetalingMedCallback.right()
                },
                kontrollsamtaleService = mock {
                    on {
                        opprettPlanlagtKontrollsamtale(
                            any(),
                            any(),
                        )
                    } doReturn OpprettPlanlagtKontrollsamtaleResultat.Opprettet(planlagtKontrollsamtale())
                },
                vedtakRepo = mock {
                    doThrow(RuntimeException()).whenever(it).lagre(any(), anyOrNull())
                },
                tilbakekrevingService = mock {
                    on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
                },
            )

            serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = innvilgetTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            ) shouldBe KunneIkkeIverksette.LagringFeilet.left()

            verify(utbetalingMedCallback, never()).sendUtbetaling()
        }
    }

    @Test
    fun `feil ved åpent kravgrunnlag`() {
        val (sak, søknadsbehandlingTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()

        SøknadsbehandlingServiceAndMocks(
            sakService = mock {
                on { hentSakForSøknadsbehandling(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn listOf(
                    IkkeTilbakekrev(
                        id = UUID.randomUUID(),
                        opprettet = Tidspunkt.now(),
                        sakId = søknadsbehandlingTilAttestering.sakId,
                        revurderingId = søknadsbehandlingTilAttestering.id,
                        periode = søknadsbehandlingTilAttestering.periode,
                    ).fullførBehandling(),
                )
            },
        ).also {
            it.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = søknadsbehandlingTilAttestering.id,
                    attestering = attesteringIverksatt(),
                ),
            ) shouldBe KunneIkkeIverksette.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving.left()
        }
    }
}
