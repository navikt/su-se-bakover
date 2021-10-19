package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.UUID30
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.idag
import no.nav.su.se.bakover.common.januar
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnholdTestdataBuilder
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.behandling.Attesteringshistorikk
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.behandling.Behandlingsinformasjon
import no.nav.su.se.bakover.domain.behandling.withAlleVilkårOppfylt
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingslinje
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.avstemming.Avstemmingsnøkkel
import no.nav.su.se.bakover.domain.oppdrag.simulering.Simulering
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeIverksette
import no.nav.su.se.bakover.domain.søknadsbehandling.StatusovergangVisitor
import no.nav.su.se.bakover.domain.søknadsbehandling.Stønadsperiode
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.domain.vedtak.snapshot.Vedtakssnapshot
import no.nav.su.se.bakover.domain.vilkår.Vilkårsvurderinger
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.behandling.BehandlingTestUtils
import no.nav.su.se.bakover.service.beregning.TestBeregning
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.person
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagMedBeregning
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
import org.mockito.kotlin.whenever
import java.time.ZoneOffset
import java.util.UUID

internal class SøknadsbehandlingServiceIverksettTest {
    private val person = person()
    private val fnr = person.ident.fnr
    private val sakId: UUID = UUID.fromString("268e62fb-3079-4e8d-ab32-ff9fb9eac2ec")
    private val behandlingId: UUID = UUID.fromString("a602aa68-c989-43e3-9fb7-cb488a2a3821")
    private val saksnummer = Saksnummer(999999)
    private val søknadOppgaveId = OppgaveId("søknadOppgaveId")
    private val attestant = NavIdentBruker.Attestant("attestant")
    private val saksbehandler = NavIdentBruker.Saksbehandler("saksbehandlinger")
    private val utbetalingId = UUID30.randomUUID()
    private val stønadsperiode = Stønadsperiode.create(Periode.create(1.januar(2021), 31.desember(2021)))
    val opprettet = fixedTidspunkt

    private val utbetaling = Utbetaling.OversendtUtbetaling.UtenKvittering(
        id = utbetalingId,
        opprettet = opprettet,
        sakId = sakId,
        saksnummer = saksnummer,
        fnr = fnr,
        utbetalingslinjer = nonEmptyListOf(
            Utbetalingslinje.Ny(
                id = UUID30.randomUUID(),
                opprettet = fixedTidspunkt,
                fraOgMed = 1.januar(2021),
                tilOgMed = 31.januar(2021),
                forrigeUtbetalingslinjeId = null,
                beløp = 0,
                uføregrad = Uføregrad.parse(50),
            ),
        ),
        type = Utbetaling.UtbetalingsType.NY,
        behandler = attestant,
        avstemmingsnøkkel = Avstemmingsnøkkel(),
        simulering = Simulering(
            gjelderId = Fnr.generer(),
            gjelderNavn = "gjelderNavn",
            datoBeregnet = opprettet.toLocalDate(ZoneOffset.UTC),
            nettoBeløp = 0,
            periodeList = listOf(),
        ),
        utbetalingsrequest = Utbetalingsrequest(value = ""),
    )

    @Test
    fun `svarer med feil dersom vi ikke finner behandling`() {
        val behandling = avslagTilAttestering()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn null
        }

        val ferdigstillVedtakServiceMock = mock<FerdigstillVedtakService>()

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
        )

        val response = serviceAndMocks.søknadsbehandlingService.iverksett(
            SøknadsbehandlingService.IverksettRequest(
                behandling.id,
                Attestering.Iverksatt(attestant, fixedTidspunkt),
            ),
        )

        response shouldBe KunneIkkeIverksette.FantIkkeBehandling.left()

        inOrder(søknadsbehandlingRepoMock, ferdigstillVedtakServiceMock) {
            verify(søknadsbehandlingRepoMock).hent(behandling.id)
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom vi ikke kunne simulere`() {
        val behandling = innvilgetTilAttestering()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { utbetal(any(), any(), any(), any(), any()) } doReturn UtbetalingFeilet.KunneIkkeSimulere(
                SimuleringFeilet.TEKNISK_FEIL,
            ).left()
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

        inOrder(
            *serviceAndMocks.allMocks(),
        ) {
            verify(søknadsbehandlingRepoMock).hent(behandling.id)
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe beregning },
                simulering = argThat { it shouldBe simulering },
                uføregrunnlag = argThat { it shouldBe emptyList() },
            )
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom kontrollsimulering var for ulik`() {
        val behandling = innvilgetTilAttestering()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on {
                utbetal(
                    any(),
                    any(),
                    any(),
                    any(),
                    any(),
                )
            } doReturn UtbetalingFeilet.SimuleringHarBlittEndretSidenSaksbehandlerSimulerte.left()
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

        inOrder(
            *serviceAndMocks.allMocks(),
        ) {
            verify(søknadsbehandlingRepoMock).hent(behandling.id)
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe beregning },
                simulering = argThat { it shouldBe simulering },
                uføregrunnlag = argThat { it shouldBe emptyList() },
            )
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `svarer med feil dersom vi ikke kunne utbetale`() {
        val behandling = innvilgetTilAttestering()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { utbetal(any(), any(), any(), any(), any()) } doReturn UtbetalingFeilet.Protokollfeil.left()
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

        response shouldBe KunneIkkeIverksette.KunneIkkeUtbetale(UtbetalingFeilet.Protokollfeil).left()

        inOrder(
            *serviceAndMocks.allMocks(),
        ) {
            verify(søknadsbehandlingRepoMock).hent(behandling.id)
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe beregning },
                simulering = argThat { it shouldBe simulering },
                uføregrunnlag = argThat { it shouldBe emptyList() },
            )
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `attesterer og iverksetter innvilgning hvis alt er ok`() {
        val behandling = innvilgetTilAttestering()

        val søknadsbehandlingRepoMock = mock<SøknadsbehandlingRepo> {
            on { hent(any()) } doReturn behandling
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { utbetal(any(), any(), any(), any(), any()) } doReturn utbetaling.right()
        }
        val behandlingMetricsMock = mock<BehandlingMetrics>()
        val vedtakRepoMock = mock<VedtakRepo>()
        val statistikkObserver = mock<EventObserver>()

        val attesteringstidspunkt = fixedTidspunkt

        val serviceAndMocks = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepoMock,
            utbetalingService = utbetalingServiceMock,
            behandlingMetrics = behandlingMetricsMock,
            vedtakRepo = vedtakRepoMock,
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
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
        )

        response shouldBe expected.right()

        inOrder(
            *serviceAndMocks.allMocks(),
        ) {
            verify(søknadsbehandlingRepoMock).hent(behandling.id)
            verify(utbetalingServiceMock).utbetal(
                sakId = argThat { it shouldBe sakId },
                attestant = argThat { it shouldBe attestant },
                beregning = argThat { it shouldBe beregning },
                simulering = argThat { it shouldBe simulering },
                uføregrunnlag = argThat { it shouldBe emptyList() },
            )
            verify(søknadsbehandlingRepoMock).defaultSessionContext()
            verify(søknadsbehandlingRepoMock).lagre(eq(expected), anyOrNull())
            verify(vedtakRepoMock).lagre(
                argThat {
                    it.behandling shouldBe expected
                    it should beOfType<Vedtak.EndringIYtelse.InnvilgetSøknadsbehandling>()
                },
            )
            verify(serviceAndMocks.opprettVedtakssnapshotService).opprettVedtak(argThat { it is Vedtakssnapshot.Innvilgelse })
            verify(behandlingMetricsMock).incrementInnvilgetCounter(BehandlingMetrics.InnvilgetHandlinger.PERSISTERT)
            verify(statistikkObserver, times(2)).handle(any())
            serviceAndMocks.verifyNoMoreInteractions()
        }
    }

    @Test
    fun `attesterer og iverksetter avslag hvis alt er ok`() {
        val attestering = Attestering.Iverksatt(attestant, fixedTidspunkt)

        val expectedAvslag = avslagTilAttestering.tilIverksatt(attestering)

        SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { hent(any()) } doReturn avslagTilAttestering
            },
            ferdigstillVedtakService = mock() { mock ->
                doAnswer {
                    (it.arguments[0] as Vedtak.Avslag.AvslagBeregning).right()
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

            inOrder(
                *it.allMocks(),
            ) {
                verify(it.søknadsbehandlingRepo).hent(avslagTilAttestering.id)
                verify(it.brevService).lagDokument(argThat { it shouldBe beOfType<Vedtak.Avslag.AvslagBeregning>() })
                verify(it.søknadsbehandlingRepo).defaultSessionContext()
                verify(it.søknadsbehandlingRepo).lagre(eq(expectedAvslag), anyOrNull())
                verify(it.vedtakRepo).lagre(argThat { it is Vedtak.Avslag.AvslagBeregning })
                verify(it.brevService).lagreDokument(
                    argThat {
                        it.metadata.sakId shouldBe avslagTilAttestering.sakId
                        it.metadata.vedtakId shouldNotBe null
                        it.metadata.bestillBrev shouldBe true
                    },
                )
                verify(it.opprettVedtakssnapshotService).opprettVedtak(any())
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
                    attestering = Attestering.Iverksatt(NavIdentBruker.Attestant(avslagTilAttestering.saksbehandler.navIdent), fixedTidspunkt),
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
        val behandling: Søknadsbehandling.Vilkårsvurdert.Innvilget = avslagTilAttestering().let {
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
                vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
                attesteringer = Attesteringshistorikk.empty(),
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

    private fun innvilgetTilAttestering() =
        Søknadsbehandling.TilAttestering.Innvilget(
            id = behandlingId,
            opprettet = opprettet,
            søknad = Søknad.Journalført.MedOppgave.IkkeLukket(
                id = BehandlingTestUtils.søknadId,
                opprettet = opprettet,
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                oppgaveId = BehandlingTestUtils.søknadOppgaveId,
                journalpostId = BehandlingTestUtils.søknadJournalpostId,
            ),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            saksbehandler = saksbehandler,
            oppgaveId = søknadOppgaveId,
            beregning = beregning,
            simulering = simulering,
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            attesteringer = Attesteringshistorikk.empty(),
        )

    private fun avslagTilAttestering() =
        Søknadsbehandling.TilAttestering.Avslag.MedBeregning(
            id = behandlingId,
            opprettet = opprettet,
            søknad = Søknad.Journalført.MedOppgave.IkkeLukket(
                id = BehandlingTestUtils.søknadId,
                opprettet = opprettet,
                sakId = sakId,
                søknadInnhold = SøknadInnholdTestdataBuilder.build(),
                oppgaveId = BehandlingTestUtils.søknadOppgaveId,
                journalpostId = BehandlingTestUtils.søknadJournalpostId,
            ),
            behandlingsinformasjon = Behandlingsinformasjon.lagTomBehandlingsinformasjon().withAlleVilkårOppfylt(),
            sakId = sakId,
            saksnummer = saksnummer,
            fnr = fnr,
            saksbehandler = saksbehandler,
            oppgaveId = søknadOppgaveId,
            beregning = beregning,
            fritekstTilBrev = "",
            stønadsperiode = stønadsperiode,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = Vilkårsvurderinger.IkkeVurdert,
            attesteringer = Attesteringshistorikk.empty(),
        )

    private val avslagTilAttestering = søknadsbehandlingTilAttesteringAvslagMedBeregning().second

    private val beregning = TestBeregning

    private val simulering = Simulering(
        gjelderId = fnr,
        gjelderNavn = "NAVN",
        datoBeregnet = idag(fixedClock),
        nettoBeløp = 191500,
        periodeList = listOf(),
    )
}
