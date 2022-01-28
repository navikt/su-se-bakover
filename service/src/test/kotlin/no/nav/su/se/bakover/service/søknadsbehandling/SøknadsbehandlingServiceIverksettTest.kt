package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.periode.desember
import no.nav.su.se.bakover.common.periode.november
import no.nav.su.se.bakover.common.periode.oktober
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.domain.avkorting.Avkortingsvarsel
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeIverksette
import no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.kontrollsamtale
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simuleringFeilutbetaling
import no.nav.su.se.bakover.test.simuleringNy
import no.nav.su.se.bakover.test.simulertUtbetaling
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.utbetalingsRequest
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
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
        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn null
            },
        )

        val response = serviceAndMocks.søknadsbehandlingService.iverksett(
            SøknadsbehandlingService.IverksettRequest(
                avslagTilAttestering.id,
                Attestering.Iverksatt(attestant, fixedTidspunkt),
            ),
        )

        response shouldBe KunneIkkeIverksette.FantIkkeBehandling.left()
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
            sakId = UUID.randomUUID(),
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
        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn innvilgetTilAttestering
            },
            utbetalingService = mock {
                on { genererUtbetalingsRequest(any(), any(), any(), any(), any()) } doReturn UtbetalingFeilet.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL).left()
            },
        )

        val response = serviceAndMocks.søknadsbehandlingService.iverksett(
            SøknadsbehandlingService.IverksettRequest(
                innvilgetTilAttestering.id,
                Attestering.Iverksatt(attestant, fixedTidspunkt),
            ),
        )

        response shouldBe KunneIkkeIverksette.KunneIkkeUtbetale(UtbetalingFeilet.KunneIkkeSimulere(SimuleringFeilet.TEKNISK_FEIL)).left()
    }

    @Test
    fun `svarer med feil dersom kontrollsimulering var for ulik`() {
        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn innvilgetTilAttestering
            },
            utbetalingService = mock {
                on { genererUtbetalingsRequest(any(), any(), any(), any(), any()) } doReturn UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
            },
        )

        val response = serviceAndMocks.søknadsbehandlingService.iverksett(
            SøknadsbehandlingService.IverksettRequest(
                behandlingId = innvilgetTilAttestering.id,
                attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
            ),
        )

        response shouldBe KunneIkkeIverksette.KunneIkkeUtbetale(UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte).left()
    }

    @Test
    fun `svarer med feil dersom vi ikke kunne utbetale`() {
        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn innvilgetTilAttestering
            },
            utbetalingService = mock {
                on { genererUtbetalingsRequest(any(), any(), any(), any(), any()) } doReturn simulertUtbetaling().right()
                on { publiserUtbetaling(any()) } doReturn UtbetalingFeilet.Protokollfeil.left()
            },
            kontrollsamtaleService = mock {
                on { opprettPlanlagtKontrollsamtale(any(), any()) } doReturn kontrollsamtale().right()
            }
        )

        val response = serviceAndMocks.søknadsbehandlingService.iverksett(
            SøknadsbehandlingService.IverksettRequest(
                behandlingId = innvilgetTilAttestering.id,
                attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
            ),
        )

        response shouldBe KunneIkkeIverksette.KunneIkkeUtbetale(UtbetalingFeilet.Protokollfeil).left()
    }

    @Test
    fun `attesterer og iverksetter innvilgning hvis alt er ok`() {
        val simulertUtbetaling = simulertUtbetaling()

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn innvilgetTilAttestering
            },
            utbetalingService = mock {
                on { genererUtbetalingsRequest(any(), any(), any(), any(), any()) } doReturn simulertUtbetaling.right()
                on { publiserUtbetaling(any()) } doReturn utbetalingsRequest.right()
            },
            kontrollsamtaleService = mock {
                on { opprettPlanlagtKontrollsamtale(any(), any()) } doReturn kontrollsamtale().right()
            },
        )

        val response = serviceAndMocks.søknadsbehandlingService.iverksett(
            SøknadsbehandlingService.IverksettRequest(
                behandlingId = innvilgetTilAttestering.id,
                attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
            ),
        )

        val expected = søknadsbehandlingIverksattInnvilget().second.copy(id = innvilgetTilAttestering.id)

        response shouldBe expected.right()

        verify(serviceAndMocks.søknadsbehandlingRepo).hent(innvilgetTilAttestering.id)
        verify(serviceAndMocks.utbetalingService).genererUtbetalingsRequest(any(), any(), any(), any(), any())
        verify(serviceAndMocks.søknadsbehandlingRepo).lagre(argThat { it shouldBe expected }, anyOrNull())
        verify(serviceAndMocks.vedtakRepo).lagre(
            argThat {
                it shouldBe vedtakSøknadsbehandlingIverksattInnvilget(utbetalingId = simulertUtbetaling.id).second.copy(id = it.id, behandling = expected)
            },
            anyOrNull()
        )
        verify(serviceAndMocks.utbetalingService).lagreUtbetaling(any(), anyOrNull())
        verify(serviceAndMocks.kontrollsamtaleService).opprettPlanlagtKontrollsamtale(
            argThat {
                it shouldBe vedtakSøknadsbehandlingIverksattInnvilget(utbetalingId = simulertUtbetaling.id).second.copy(id = it.id, behandling = expected)
            },
            anyOrNull()
        )
        verify(serviceAndMocks.utbetalingService).publiserUtbetaling(argThat { it shouldBe simulertUtbetaling })
        verify(serviceAndMocks.behandlingMetrics).incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)
        verify(serviceAndMocks.observer, times(2)).handle(any())
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `attesterer og iverksetter avslag hvis alt er ok`() {
        val attestering = Attestering.Iverksatt(attestant, fixedTidspunkt)
        val expectedAvslag = avslagTilAttestering.tilIverksatt(attestering)

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
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
        )

        serviceAndMocks.søknadsbehandlingService.iverksett(
            SøknadsbehandlingService.IverksettRequest(
                behandlingId = avslagTilAttestering.id,
                attestering = attestering,
            ),
        ) shouldBe expectedAvslag.right()

        verify(serviceAndMocks.søknadsbehandlingRepo).hent(avslagTilAttestering.id)
        verify(serviceAndMocks.brevService).lagDokument(argThat { it shouldBe beOfType<Avslagsvedtak.AvslagBeregning>() })
        verify(serviceAndMocks.søknadsbehandlingRepo).lagre(eq(expectedAvslag), anyOrNull())
        verify(serviceAndMocks.vedtakRepo).lagre(argThat { it is Avslagsvedtak.AvslagBeregning }, anyOrNull())
        verify(serviceAndMocks.brevService).lagreDokument(
            argThat {
                it.metadata.sakId shouldBe avslagTilAttestering.sakId
                it.metadata.vedtakId shouldNotBe null
                it.metadata.bestillBrev shouldBe true
            },
            anyOrNull()
        )
        verify(serviceAndMocks.behandlingMetrics).incrementAvslåttCounter(BehandlingMetrics.AvslåttHandlinger.PERSISTERT)
        verify(serviceAndMocks.ferdigstillVedtakService).lukkOppgaveMedBruker(any())
        verify(serviceAndMocks.observer).handle(
            argThat {
                it shouldBe Event.Statistikk.SøknadsbehandlingStatistikk.SøknadsbehandlingIverksatt(expectedAvslag)
            },
        )
        serviceAndMocks.verifyNoMoreInteractions()
    }

    @Test
    fun `attestant og saksbehandler kan ikke være samme person`() {
        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn avslagTilAttestering
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

    @Test
    fun `svarer med feil dersom generering av vedtaksbrev feiler`() {
        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn avslagTilAttestering
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn KunneIkkeLageDokument.KunneIkkeHentePerson.left()
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
    fun `iverksett behandling kaster exception ved ugyldig statusovergang`() {
        val behandling = søknadsbehandlingVilkårsvurdertInnvilget().second

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn behandling
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
    fun `verifiser at utbetaling skjer etter alle lagre-kall`() {
        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn innvilgetTilAttestering
            },
            utbetalingService = mock {
                on { genererUtbetalingsRequest(any(), any(), any(), any(), any()) } doReturn simulertUtbetaling().right()
                on { publiserUtbetaling(any()) } doReturn utbetalingsRequest.right()
            },
            kontrollsamtaleService = mock {
                on { opprettPlanlagtKontrollsamtale(any(), any()) } doReturn kontrollsamtale().right()
            },
        )

        serviceAndMocks.søknadsbehandlingService.iverksett(
            SøknadsbehandlingService.IverksettRequest(
                behandlingId = innvilgetTilAttestering.id,
                attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
            ),
        )

        inOrder(*serviceAndMocks.allMocks()) {
            verify(serviceAndMocks.søknadsbehandlingRepo).lagre(any(), anyOrNull())
            verify(serviceAndMocks.utbetalingService).lagreUtbetaling(any(), anyOrNull())
            verify(serviceAndMocks.vedtakRepo).lagre(any(), anyOrNull())
            verify(serviceAndMocks.kontrollsamtaleService).opprettPlanlagtKontrollsamtale(any(), anyOrNull())
            verify(serviceAndMocks.utbetalingService).publiserUtbetaling(any())
        }
    }

    private val attestant = NavIdentBruker.Attestant("attestant")
    private val avslagTilAttestering = søknadsbehandlingTilAttesteringAvslagMedBeregning().second
    private val innvilgetTilAttestering = søknadsbehandlingTilAttesteringInnvilget().second
}
