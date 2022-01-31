package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.periode.oktober
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalRequest
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeIverksette
import no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.vedtak.VedtakSomKanRevurderes
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.kontrollsamtale.KontrollsamtaleService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.kontrollsamtale
import no.nav.su.se.bakover.test.oversendtUtbetalingUtenKvittering
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.simuleringNy
import no.nav.su.se.bakover.test.simulertUtbetaling
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.utbetalingsRequest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

internal class SøknadsbehandlingServiceIverksettTest {

    @Test
    fun `svarer med feil dersom vi ikke finner behandling`() {
        val behandlingId = avslagTilAttestering.id

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService>()

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        )

        val response = serviceAndMocks.søknadsbehandlingService.iverksett(
            SøknadsbehandlingService.IverksettRequest(
                behandlingId,
                Attestering.Iverksatt(attestant, fixedTidspunkt),
            ),
        )

        response shouldBe KunneIkkeIverksette.FantIkkeBehandling.left()

        inOrder(søknadsbehandlingRepoMock, ferdigstillVedtakServiceMock) {
            verify(søknadsbehandlingRepoMock).hent(behandlingId)
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `feiler hvis utestående avkortinger ikke kunne avkortes fullstendig`() {
        val tilAttestering = søknadsbehandlingVilkårsvurdertInnvilget().let { (_, vilkårsvurdert) ->
            vilkårsvurdert.leggTilFradragsgrunnlag(
                fradragsgrunnlag = listOf(
                    fradragsgrunnlagArbeidsinntekt(
                        arbeidsinntekt = 20000.0,
                    ),
                ),
            ).getOrFail().beregn(
                begrunnelse = null,
                clock = fixedClock,
            ).getOrFail().let {
                it.tilSimulert(simuleringNy(it.beregning))
            }.tilAttestering(
                saksbehandler = saksbehandler,
                fritekstTilBrev = "njet",
            )
        }

        val avkortingsvarsel = Avkortingsvarsel.Utenlandsopphold.Opprettet(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            sakId = sakId,
            revurderingId = UUID.randomUUID(),
            simulering = simuleringFeilutbetaling(
                oktober(2020), november(2020), desember(2020),
            ),
        ).skalAvkortes()

        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn tilAttestering.copy(
                    avkorting = AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående(
                        avkortingsvarsel = avkortingsvarsel,
                    ),
                )
            },
            avkortingsvarselRepo = mock {
                on { hent(any()) } doReturn avkortingsvarsel
            },

        ).let {
            it.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    tilAttestering.id,
                    Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            ) shouldBe KunneIkkeIverksette.AvkortingErUfullstendig.left()

            verify(it.søknadsbehandlingRepo).hent(tilAttestering.id)
            verify(it.avkortingsvarselRepo).hent(avkortingsvarsel.id)
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom vi ikke kunne simulere`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { genererUtbetalingsRequest(any()) } doReturn UtbetalingFeilet.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL)
                .left()
        }

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        )

        val response = serviceAndMocks.søknadsbehandlingService.iverksett(
            SøknadsbehandlingService.IverksettRequest(
                behandling.id,
                Attestering.Iverksatt(attestant, fixedTidspunkt),
            ),
        )

        response shouldBe KunneIkkeIverksette.KunneIkkeUtbetale(UtbetalingFeilet.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL))
            .left()

        verify(søknadsbehandlingRepoMock).hent(behandling.id)
        verify(utbetalingServiceMock).genererUtbetalingsRequest(
            request = argThat {
                it shouldBe UtbetalRequest.NyUtbetaling(
                    request = SimulerUtbetalingRequest.NyUtbetaling(
                        sakId = sakId,
                        saksbehandler = attestant,
                        beregning = behandling.beregning,
                        uføregrunnlag = emptyList(),
                    ),
                    simulering = simulering,
                )
            },
        )
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `svarer med feil dersom kontrollsimulering var for ulik`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { genererUtbetalingsRequest(any()) } doReturn UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
        }

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
        )

        val response = serviceAndMocks.søknadsbehandlingService.iverksett(
            SøknadsbehandlingService.IverksettRequest(
                behandlingId = behandling.id,
                attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
            ),
        )

        response shouldBe KunneIkkeIverksette.KunneIkkeUtbetale(UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte)
            .left()

        verify(søknadsbehandlingRepoMock).hent(behandling.id)
        verify(utbetalingServiceMock).genererUtbetalingsRequest(
            request = argThat {
                it shouldBe UtbetalRequest.NyUtbetaling(
                    request = SimulerUtbetalingRequest.NyUtbetaling(
                        sakId = sakId,
                        saksbehandler = attestant,
                        beregning = behandling.beregning,
                        uføregrunnlag = emptyList(),
                    ),
                    simulering = simulering,
                )
            }
        )
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `svarer med feil dersom vi ikke kunne utbetale`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { genererUtbetalingsRequest(any()) } doReturn simulertUtbetaling().right()
            on { publiserUtbetaling(any()) } doReturn UtbetalingFeilet.Protokollfeil.left()
        }
        val vedtakRepoMock = mock<VedtakRepo>()
        val kontrollsamtaleServiceMock = mock<KontrollsamtaleService> {
            on { opprettPlanlagtKontrollsamtale(any(), any()) } doReturn kontrollsamtale().right()
        }

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            vedtakRepo = vedtakRepoMock,
            kontrollsamtaleService = kontrollsamtaleServiceMock,
        )

        val response = serviceAndMocks.søknadsbehandlingService.iverksett(
            SøknadsbehandlingService.IverksettRequest(
                behandlingId = behandling.id,
                attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
            ),
        )

        response shouldBe KunneIkkeIverksette.KunneIkkeUtbetale(UtbetalingFeilet.Protokollfeil).left()

        verify(søknadsbehandlingRepoMock).hent(behandling.id)
        verify(søknadsbehandlingRepoMock).lagre(any(), anyOrNull())
        verify(utbetalingServiceMock).genererUtbetalingsRequest(
            request = argThat {
                it shouldBe UtbetalRequest.NyUtbetaling(
                    request = SimulerUtbetalingRequest.NyUtbetaling(
                        sakId = behandling.sakId,
                        saksbehandler = attestant,
                        beregning = behandling.beregning,
                        uføregrunnlag = emptyList(),
                    ),
                    simulering = simulering,
                )
            },
        )
        verify(vedtakRepoMock).lagre(any(), anyOrNull())
        verify(utbetalingServiceMock).lagreUtbetaling(any(), anyOrNull())
        verify(kontrollsamtaleServiceMock).opprettPlanlagtKontrollsamtale(any(), anyOrNull())
        verify(utbetalingServiceMock).publiserUtbetaling(any())
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `attesterer og iverksetter innvilgning hvis alt er ok`() {
        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val simulertUtbetaling = simulertUtbetaling()

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { genererUtbetalingsRequest(any()) } doReturn simulertUtbetaling.right()
            on { publiserUtbetaling(any()) } doReturn utbetalingsRequest.right()
        }
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val vedtakRepoMock = mock<VedtakRepo>()
        val kontrollsamtaleServiceMock = mock<KontrollsamtaleService> {
            on { opprettPlanlagtKontrollsamtale(any(), any()) } doReturn kontrollsamtale().right()
        }
        val statistikkObserver = mock<EventObserver>()

        val attesteringstidspunkt = fixedTidspunkt

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            vedtakRepo = vedtakRepoMock,
            kontrollsamtaleService = kontrollsamtaleServiceMock,
            observer = statistikkObserver,
        )
        val response = serviceAndMocks.søknadsbehandlingService.iverksett(
            SøknadsbehandlingService.IverksettRequest(
                behandlingId = behandling.id,
                attestering = Attestering.Iverksatt(attestant, attesteringstidspunkt),
            ),
        )

        val expected = Søknadsbehandling.Iverksatt.Innvilget(
            id = behandling.id,
            opprettet = behandling.opprettet,
            sakId = behandling.sakId,
            saksnummer = behandling.saksnummer,
            søknad = behandling.søknad,
            oppgaveId = behandling.oppgaveId,
            behandlingsinformasjon = behandling.behandlingsinformasjon,
            fnr = behandling.fnr,
            beregning = behandling.beregning,
            simulering = behandling.simulering,
            saksbehandler = behandling.saksbehandler,
            attesteringer = Attesteringshistorikk.empty()
                .leggTilNyAttestering(Attestering.Iverksatt(attestant, attesteringstidspunkt)),
            fritekstTilBrev = "",
            stønadsperiode = behandling.stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling.IkkeVurdert,
            avkorting = AvkortingVedSøknadsbehandling.Iverksatt.IngenUtestående,
        )

        response shouldBe expected.right()

        verify(søknadsbehandlingRepoMock).hent(behandling.id)
        verify(utbetalingServiceMock).genererUtbetalingsRequest(
            request = argThat {
                it shouldBe UtbetalRequest.NyUtbetaling(
                    request = SimulerUtbetalingRequest.NyUtbetaling(
                        sakId = behandling.sakId,
                        saksbehandler = attestant,
                        beregning = behandling.beregning,
                        uføregrunnlag = emptyList(),
                    ),
                    simulering = simulering,
                )
            },
        )
        verify(søknadsbehandlingRepoMock).lagre(eq(expected), anyOrNull())
        var vedtak: VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling? = null
        verify(vedtakRepoMock).lagre(
            argThat {
                it should beOfType<VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling>()
                vedtak = it as VedtakSomKanRevurderes.EndringIYtelse.InnvilgetSøknadsbehandling
                it.behandling shouldBe expected
            },
            anyOrNull(),
        )
        verify(utbetalingServiceMock).lagreUtbetaling(any(), anyOrNull())
        verify(kontrollsamtaleServiceMock).opprettPlanlagtKontrollsamtale(argThat { it shouldBe vedtak }, anyOrNull())
        verify(utbetalingServiceMock).publiserUtbetaling(argThat { it shouldBe simulertUtbetaling })
        verify(behandlingMetricsMock).incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)
        verify(statistikkObserver, times(2)).handle(any())
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `attesterer og iverksetter avslag hvis alt er ok`() {
        val attestering = Attestering.Iverksatt(attestant, fixedTidspunkt)
        val expectedAvslag = avslagTilAttestering.tilIverksatt(attestering)

        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn avslagTilAttestering
            },
            ferdigstillVedtakService = mock { mock ->
                doAnswer {
                    (it.arguments[0]).right()
                }.whenever(mock).lukkOppgaveMedBruker(any())
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn Dokument.UtenMetadata.Vedtak(
                    id = UUID.randomUUID(),
                    opprettet = fixedTidspunkt,
                    tittel = "tittel1",
                    generertDokument = "".toByteArray(),
                    generertDokumentJson = "",
                ).right()
            },
        ).let {
            it.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = avslagTilAttestering.id,
                    attestering = attestering,
                ),
            ) shouldBe expectedAvslag.right()

            verify(it.søknadsbehandlingRepo).hent(avslagTilAttestering.id)
            verify(it.brevService).lagDokument(argThat { it shouldBe beOfType<Avslagsvedtak.AvslagBeregning>() })
            verify(it.søknadsbehandlingRepo).lagre(eq(expectedAvslag), anyOrNull())
            verify(it.vedtakRepo).lagre(argThat { it is Avslagsvedtak.AvslagBeregning }, anyOrNull())
            verify(it.brevService).lagreDokument(
                argThat {
                    it.metadata.sakId shouldBe avslagTilAttestering.sakId
                    it.metadata.vedtakId shouldNotBe null
                    it.metadata.bestillBrev shouldBe true
                },
                anyOrNull(),
            )
            verify(it.behandlingMetrics).incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.PERSISTERT)
            verify(it.ferdigstillVedtakService).lukkOppgaveMedBruker(any())
            verify(it.observer).handle(
                argThat {
                    it shouldBe Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingIverksatt(
                        expectedAvslag,
                    )
                },
            )
            it.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `iverksett behandling attesterer og saksbehandler kan ikke være samme person`() {
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn avslagTilAttestering
            },
        ).let {
            it.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = avslagTilAttestering.id,
                    attestering = Attestering.Iverksatt(
                        NavIdentBruker.Attestant(avslagTilAttestering.saksbehandler.navIdent),
                        fixedTidspunkt,
                    ),
                ),
            ) shouldBe KunneIkkeIverksette.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()

            inOrder(
                *it.allMocks(),
            ) {
                verify(it.søknadsbehandlingRepo).hent(avslagTilAttestering.id)
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `svarer med feil dersom generering av vedtaksbrev feiler`() {
        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn avslagTilAttestering
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn KunneIkkeLageDokument.KunneIkkeHentePerson.left()
            },
        ).let {
            it.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = avslagTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            ) shouldBe KunneIkkeIverksette.KunneIkkeGenerereVedtaksbrev.left()

            inOrder(
                *it.allMocks(),
            ) {
                verify(it.søknadsbehandlingRepo).hent(avslagTilAttestering.id)
                verify(it.brevService).lagDokument(any())
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `iverksett behandling kaster exception ved ugyldig statusovergang`() {
        val behandling: Søknadsbehandling.Vilkårsvurdert.Innvilget = avslagTilAttestering.let {
            Søknadsbehandling.Vilkårsvurdert.Innvilget(
                id = it.id,
                opprettet = it.opprettet,
                sakId = it.sakId,
                saksnummer = it.saksnummer,
                søknad = it.søknad,
                oppgaveId = søknadOppgaveId,
                behandlingsinformasjon = it.behandlingsinformasjon,
                fnr = it.fnr,
                fritekstTilBrev = "",
                stønadsperiode = it.stønadsperiode,
                grunnlagsdata = Grunnlagsdata.IkkeVurdert,
                vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling.IkkeVurdert,
                attesteringer = Attesteringshistorikk.empty(),
                avkorting = AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående,
            )
        }

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        )

        assertThrows<StatusovergangVisitor.UgyldigStatusovergangException> {
            serviceAndMocks.søknadsbehandlingService.iverksett(
                SøknadsbehandlingService.IverksettRequest(
                    behandlingId = behandling.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            )

            inOrder(
                *serviceAndMocks.allMocks(),
            ) {
                verify(søknadsbehandlingRepoMock).hent(behandling.id)
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }

    private val sakId: UUID = UUID.fromString("268e62fb-3079-4e8d-ab32-ff9fb9eac2ec")
    private val søknadOppgaveId = OppgaveId("søknadOppgaveId")
    private val attestant = NavIdentBruker.Attestant("attestant")
    private val utbetaling = oversendtUtbetalingUtenKvittering(sakId = sakId)
    private val avslagTilAttestering = søknadsbehandlingTilAttesteringAvslagMedBeregning().second
    private val simulering = Simulering(
        gjelderId = person().ident.fnr,
        gjelderNavn = "NAVN",
        datoBeregnet = idag(fixedClock),
        nettoBeløp = 191500,
        periodeList = listOf(),
    )

    private val behandling = søknadsbehandlingTilAttesteringInnvilget().second.copy(
        sakId = sakId,
        simulering = simulering,
        vilkårsvurderinger = Vilkårsvurderinger.Søknadsbehandling.IkkeVurdert,
        behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
        grunnlagsdata = Grunnlagsdata.IkkeVurdert,
    )
}
