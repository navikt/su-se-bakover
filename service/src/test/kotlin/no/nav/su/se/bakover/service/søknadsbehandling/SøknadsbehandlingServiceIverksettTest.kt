package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.domain.Attestering
import no.nav.su.se.bakover.common.domain.Attesteringshistorikk
import no.nav.su.se.bakover.common.extensions.desember
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.person.Fnr
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.brev.command.IverksettSøknadsbehandlingDokumentCommand
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.oppdrag.KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingFeilet
import no.nav.su.se.bakover.domain.oppdrag.UtbetalingKlargjortForOversendelse
import no.nav.su.se.bakover.domain.oppdrag.Utbetalingsrequest
import no.nav.su.se.bakover.domain.oppdrag.simulering.SimuleringFeilet
import no.nav.su.se.bakover.domain.sak.SakService
import no.nav.su.se.bakover.domain.sak.SimulerUtbetalingFeilet
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.statistikk.StatistikkEventObserver
import no.nav.su.se.bakover.domain.søknadsbehandling.IverksattSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.IverksettSøknadsbehandlingCommand
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.OpprettKontrollsamtaleVedNyStønadsperiodeService
import no.nav.su.se.bakover.domain.vedtak.Avslagsvedtak
import no.nav.su.se.bakover.domain.vedtak.VedtakAvslagBeregning
import no.nav.su.se.bakover.domain.vedtak.VedtakInnvilgetSøknadsbehandling
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.service.skatt.SkattDokumentService
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.attesteringIverksatt
import no.nav.su.se.bakover.test.dokumentUtenMetadataVedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.generer
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.nySøknadsbehandlingshistorikkSendtTilAttesteringAvslåttBeregning
import no.nav.su.se.bakover.test.satsFactoryTestPåDato
import no.nav.su.se.bakover.test.simulering.simulerUtbetaling
import no.nav.su.se.bakover.test.søknad.nySøknadJournalførtMedOppgave
import no.nav.su.se.bakover.test.søknad.personopplysninger
import no.nav.su.se.bakover.test.søknad.søknadinnholdUføre
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagMedBeregning
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringInnvilget
import no.nav.su.se.bakover.test.søknadsbehandlingVilkårsvurdertInnvilget
import no.nav.su.se.bakover.test.tilAttesteringSøknadsbehandling
import no.nav.su.se.bakover.test.utbetaling.utbetalingsRequest
import no.nav.su.se.bakover.test.vedtakRevurdering
import org.junit.jupiter.api.Disabled
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
import java.time.Clock
import java.util.UUID

internal class SøknadsbehandlingServiceIverksettTest {

    @Nested
    inner class GenerelleFeil {
        @Test
        fun `svarer med feil dersom vi ikke finner behandling`() {
            val serviceAndMocks = ServiceAndMocks(
                sakOgSøknadsbehandling = null,
            )

            shouldThrow<NullPointerException> {
                serviceAndMocks.service.iverksett(
                    IverksettSøknadsbehandlingCommand(
                        UUID.randomUUID(),
                        Attestering.Iverksatt(attestant, fixedTidspunkt),
                    ),
                )
            }.message shouldBe "Kan ikke hente sak for søknadsbehandling id. Eksisterer IDen? Denne feilmeldingen er generert vha. en mock."
            verify(serviceAndMocks.sakService).hentSakForSøknadsbehandling(any())
            serviceAndMocks.verifyNoMoreInteractions()
        }

        @Test
        fun `kaster exception dersom søknadsbehandlinga ikke er i tilstanden til attestering`() {
            val sakOgSøknadsbehandling = søknadsbehandlingVilkårsvurdertInnvilget()

            val serviceAndMocks = ServiceAndMocks(
                sakOgSøknadsbehandling = sakOgSøknadsbehandling,
            )
            assertThrows<IllegalArgumentException> {
                serviceAndMocks.service.iverksett(
                    IverksettSøknadsbehandlingCommand(
                        behandlingId = sakOgSøknadsbehandling.second.id,
                        attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                    ),
                )
            }.message shouldContain "Prøvde iverksette søknadsbehandling som ikke var til attestering"
        }

        @Test
        fun `svarer med feil dersom vi ikke kunne simulere`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()
            val serviceAndMocks = ServiceAndMocks(
                sakOgSøknadsbehandling = Pair(sak, innvilgetTilAttestering),
                utbetalingService = mock {
                    on { simulerUtbetaling(any(), any()) } doReturn SimuleringFeilet.TekniskFeil.left()
                },
            )

            val response = serviceAndMocks.service.iverksett(
                IverksettSøknadsbehandlingCommand(
                    innvilgetTilAttestering.id,
                    Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            )

            response shouldBe KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale(
                UtbetalingFeilet.KunneIkkeSimulere(
                    SimulerUtbetalingFeilet.FeilVedSimulering(SimuleringFeilet.TekniskFeil),
                ),
            ).left()
        }

        @Test
        fun `svarer med feil dersom kontrollsimulering var for ulik`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget(
                clock = TikkendeKlokke(fixedClock),
            )
            val serviceAndMocks = ServiceAndMocks(
                sakOgSøknadsbehandling = Pair(sak, innvilgetTilAttestering),
                utbetalingService = mock {
                    doAnswer { invocation ->
                        simulerUtbetaling(
                            sak,
                            (invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering).copy(fnr = Fnr.generer()),
                            invocation.getArgument(1) as Periode,
                            TikkendeKlokke(fixedClock),
                        )
                    }.whenever(it).simulerUtbetaling(any(), any())
                },
            )

            val response = serviceAndMocks.service.iverksett(
                IverksettSøknadsbehandlingCommand(
                    behandlingId = innvilgetTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            )

            response shouldBe KunneIkkeIverksetteSøknadsbehandling.KunneIkkeUtbetale(
                UtbetalingFeilet.KunneIkkeSimulere(
                    SimulerUtbetalingFeilet.FeilVedKryssjekkAvSaksbehandlerOgAttestantsSimulering(
                        KryssjekkAvSaksbehandlersOgAttestantsSimuleringFeilet.UlikGjelderId,
                    ),
                ),
            ).left()
        }

        @Test
        fun `attestant og saksbehandler kan ikke være samme person`() {
            val (sak, avslagTilAttestering) = søknadsbehandlingTilAttesteringAvslagMedBeregning()
            val serviceAndMocks = ServiceAndMocks(
                sakOgSøknadsbehandling = Pair(sak, avslagTilAttestering),
            )

            val response = serviceAndMocks.service.iverksett(
                IverksettSøknadsbehandlingCommand(
                    behandlingId = avslagTilAttestering.id,
                    attestering = Attestering.Iverksatt(
                        NavIdentBruker.Attestant(avslagTilAttestering.saksbehandler.navIdent),
                        fixedTidspunkt,
                    ),
                ),
            )

            response shouldBe KunneIkkeIverksetteSøknadsbehandling.AttestantOgSaksbehandlerKanIkkeVæreSammePerson.left()
        }
    }

    @Nested
    inner class Avslag {
        @Test
        fun `svarer med feil dersom generering av vedtaksbrev feiler`() {
            val (sak, avslagTilAttestering) = søknadsbehandlingTilAttesteringAvslagMedBeregning()
            val underliggendeFeil = KunneIkkeLageDokument.FeilVedGenereringAvPdf
            val serviceAndMocks = ServiceAndMocks(
                sakOgSøknadsbehandling = Pair(sak, avslagTilAttestering),
                sakService = mock {
                    on { hentSakForSøknadsbehandling(any()) } doReturn sak
                },
                søknadsbehandlingRepo = mock {
                    on { hent(any()) } doReturn avslagTilAttestering
                },
                brevService = mock {
                    on { lagDokument(any()) } doReturn underliggendeFeil.left()
                },
            )
            val response = serviceAndMocks.service.iverksett(
                IverksettSøknadsbehandlingCommand(
                    behandlingId = avslagTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            )
            response shouldBe KunneIkkeIverksetteSøknadsbehandling.KunneIkkeGenerereVedtaksbrev(
                underliggendeFeil,
            ).left()
        }

        @Test
        fun `attesterer og iverksetter avslag hvis alt er ok`() {
            val attestering = Attestering.Iverksatt(attestant, fixedTidspunkt)
            val (sak, avslagTilAttestering) = søknadsbehandlingTilAttesteringAvslagMedBeregning()
            val expectedAvslag = IverksattSøknadsbehandling.Avslag.MedBeregning(
                id = avslagTilAttestering.id,
                opprettet = avslagTilAttestering.opprettet,
                sakId = sak.id,
                saksnummer = sak.saksnummer,
                søknad = avslagTilAttestering.søknad,
                oppgaveId = avslagTilAttestering.oppgaveId,
                fnr = sak.fnr,
                beregning = avslagTilAttestering.beregning,
                saksbehandler = avslagTilAttestering.saksbehandler,
                attesteringer = Attesteringshistorikk.create(listOf(attestering)),
                fritekstTilBrev = avslagTilAttestering.fritekstTilBrev,
                aldersvurdering = avslagTilAttestering.aldersvurdering,
                grunnlagsdataOgVilkårsvurderinger = avslagTilAttestering.grunnlagsdataOgVilkårsvurderinger,
                sakstype = avslagTilAttestering.sakstype,
                søknadsbehandlingsHistorikk = nySøknadsbehandlingshistorikkSendtTilAttesteringAvslåttBeregning(
                    saksbehandler = avslagTilAttestering.saksbehandler,
                ),
            )

            val serviceAndMocks = ServiceAndMocks(
                sakOgSøknadsbehandling = Pair(sak, avslagTilAttestering),
            )

            serviceAndMocks.service.iverksett(
                IverksettSøknadsbehandlingCommand(
                    behandlingId = avslagTilAttestering.id,
                    attestering = attestering,
                ),
            ).getOrFail().second shouldBe expectedAvslag

            verify(serviceAndMocks.sakService).hentSakForSøknadsbehandling(avslagTilAttestering.id)
            verify(serviceAndMocks.brevService).lagDokument(argThat { it shouldBe beOfType<IverksettSøknadsbehandlingDokumentCommand.Avslag>() })
            verify(serviceAndMocks.søknadsbehandlingRepo).lagre(eq(expectedAvslag), anyOrNull())
            verify(serviceAndMocks.vedtakRepo).lagreITransaksjon(
                argThat { it is VedtakAvslagBeregning },
                anyOrNull(),
            )
            verify(serviceAndMocks.brevService).lagreDokument(
                argThat {
                    it.metadata.sakId shouldBe avslagTilAttestering.sakId
                    it.metadata.vedtakId shouldNotBe null
                },
                anyOrNull(),
            )
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
            val serviceAndMocks = ServiceAndMocks(
                søknadsbehandlingRepo = mock {
                    doThrow(RuntimeException("kastet fra testen.")).whenever(it).lagre(any(), anyOrNull())
                },
                sakOgSøknadsbehandling = Pair(sak, innvilgetTilAttestering),
            )

            shouldThrow<RuntimeException> {
                serviceAndMocks.service.iverksett(
                    IverksettSøknadsbehandlingCommand(
                        behandlingId = innvilgetTilAttestering.id,
                        attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                    ),
                )
            }.message shouldBe "kastet fra testen."

            verify(utbetalingMedCallback, never()).sendUtbetaling()
        }

        @Test
        fun `avslår ikke dersom vi ikke kan lagre vedtaket`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringAvslagMedBeregning()
            val utbetalingMedCallback = mock<UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>()
            val serviceAndMocks = ServiceAndMocks(
                sakOgSøknadsbehandling = Pair(sak, innvilgetTilAttestering),
                vedtakRepo = mock {
                    doThrow(RuntimeException("kastet fra testen.")).whenever(it).lagreITransaksjon(any(), anyOrNull())
                },
            )

            shouldThrow<RuntimeException> {
                serviceAndMocks.service.iverksett(
                    IverksettSøknadsbehandlingCommand(
                        behandlingId = innvilgetTilAttestering.id,
                        attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                    ),
                )
            }.message shouldBe "kastet fra testen."

            verify(utbetalingMedCallback, never()).sendUtbetaling()
        }

        @Test
        fun `avslår ikke dersom vi ikke kan lagre brevet`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringAvslagMedBeregning()
            val utbetalingMedCallback = mock<UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>()
            val serviceAndMocks = ServiceAndMocks(
                sakOgSøknadsbehandling = Pair(sak, innvilgetTilAttestering),
                brevService = mock {
                    on { lagDokument(any()) } doReturn dokumentUtenMetadataVedtak().right()
                    doThrow(RuntimeException("kastet fra testen.")).whenever(it).lagreDokument(any(), anyOrNull())
                },
            )

            shouldThrow<RuntimeException> {
                serviceAndMocks.service.iverksett(
                    IverksettSøknadsbehandlingCommand(
                        behandlingId = innvilgetTilAttestering.id,
                        attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                    ),
                )
            }.message shouldBe "kastet fra testen."

            verify(utbetalingMedCallback, never()).sendUtbetaling()
        }
    }

    @Nested
    inner class Innvilgelse {

        @Test
        fun `svarer med feil dersom vi ikke kunne utbetale`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()

            val serviceAndMocks = ServiceAndMocks(
                sakOgSøknadsbehandling = Pair(sak, innvilgetTilAttestering),
                utbetalingKlargjortForOversendelseCallback = mock {
                    on { it.invoke(any()) } doReturn UtbetalingFeilet.Protokollfeil.left()
                },
            )
            shouldThrow<RuntimeException> {
                serviceAndMocks.service.iverksett(
                    IverksettSøknadsbehandlingCommand(
                        behandlingId = innvilgetTilAttestering.id,
                        attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                    ),
                )
            }.message shouldContain "Protokollfeil"
        }

        @Test
        fun `attesterer og iverksetter innvilgning hvis alt er ok`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()

            val serviceAndMocks = ServiceAndMocks(
                sakOgSøknadsbehandling = Pair(sak, innvilgetTilAttestering),
            )
            val attestering = Attestering.Iverksatt(attestant, fixedTidspunkt)

            val (_, actualSøknadsbehandling, actualVedtak) =
                serviceAndMocks.service.iverksett(
                    IverksettSøknadsbehandlingCommand(
                        behandlingId = innvilgetTilAttestering.id,
                        attestering = attestering,
                    ),
                ).getOrFail().let {
                    @Suppress("UNCHECKED_CAST")
                    it as Triple<Sak, IverksattSøknadsbehandling.Innvilget, VedtakInnvilgetSøknadsbehandling>
                }

            actualSøknadsbehandling.attesteringer shouldBe Attesteringshistorikk.create(listOf(attestering))

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
                søknadsbehandling = argThat { it shouldBe actualSøknadsbehandling },
                sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.vedtakRepo).lagreITransaksjon(
                vedtak = argThat {
                    it shouldBe actualVedtak
                },
                tx = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.opprettPlanlagtKontrollsamtaleService).opprett(
                vedtak = argThat {
                    it shouldBe actualVedtak
                },
                sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
            )
            verify(serviceAndMocks.utbetalingKlargjortForOversendelseCallback).invoke(utbetalingsRequest)
            verify(serviceAndMocks.observer, times(2)).handle(any())
            serviceAndMocks.verifyNoMoreInteractions()
        }

        @Test
        fun `verifiser at utbetaling skjer etter alle lagre-kall`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()

            val serviceAndMocks = ServiceAndMocks(
                sakOgSøknadsbehandling = Pair(sak, innvilgetTilAttestering),
            )

            serviceAndMocks.service.iverksett(
                IverksettSøknadsbehandlingCommand(
                    behandlingId = innvilgetTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            )

            inOrder(*serviceAndMocks.allMocks(), serviceAndMocks.utbetalingKlargjortForOversendelseCallback) {
                verify(serviceAndMocks.søknadsbehandlingRepo).lagre(
                    søknadsbehandling = any(),
                    sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
                )
                verify(serviceAndMocks.utbetalingService).klargjørUtbetaling(
                    utbetaling = any(),
                    transactionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
                )
                verify(serviceAndMocks.vedtakRepo).lagreITransaksjon(
                    vedtak = any(),
                    tx = argThat { it shouldBe TestSessionFactory.transactionContext },
                )

                verify(serviceAndMocks.opprettPlanlagtKontrollsamtaleService).opprett(
                    vedtak = any(),
                    sessionContext = argThat { it shouldBe TestSessionFactory.transactionContext },
                )
                verify(serviceAndMocks.utbetalingKlargjortForOversendelseCallback).invoke(utbetalingsRequest)
            }
        }

        @Test
        fun `utbetaler selvom det allerede finnes en planlagt kontrollsamtale`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()

            val serviceAndMocks = ServiceAndMocks(
                sakOgSøknadsbehandling = Pair(sak, innvilgetTilAttestering),
                opprettPlanlagtKontrollsamtaleService = mock { },
            )

            serviceAndMocks.service.iverksett(
                IverksettSøknadsbehandlingCommand(
                    behandlingId = innvilgetTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            ).shouldBeRight()

            verify(serviceAndMocks.utbetalingKlargjortForOversendelseCallback).invoke(utbetalingsRequest)
        }

        @Test
        fun `utbetaler selvom vi ikke skal planlegge en kontrollsamtale`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()

            val serviceAndMocks = ServiceAndMocks(
                sakOgSøknadsbehandling = Pair(sak, innvilgetTilAttestering),
            )

            serviceAndMocks.service.iverksett(
                IverksettSøknadsbehandlingCommand(
                    behandlingId = innvilgetTilAttestering.id,
                    attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                ),
            ).shouldBeRight()

            verify(serviceAndMocks.utbetalingKlargjortForOversendelseCallback).invoke(utbetalingsRequest)
        }

        @Test
        fun `utbetaler ikke dersom vi ikke kan lagre behandlinga`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()
            val utbetalingMedCallback = mock<UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>()
            val serviceAndMocks = ServiceAndMocks(
                sakOgSøknadsbehandling = Pair(sak, innvilgetTilAttestering),
                søknadsbehandlingRepo = mock {
                    on { lagre(any(), any()) } doThrow RuntimeException("Mocking: Kan ikke lagre søkndsbehandling")
                },
            )
            shouldThrow<RuntimeException> {
                serviceAndMocks.service.iverksett(
                    IverksettSøknadsbehandlingCommand(
                        behandlingId = innvilgetTilAttestering.id,
                        attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                    ),
                )
            }.message shouldBe "Mocking: Kan ikke lagre søkndsbehandling"

            verify(utbetalingMedCallback, never()).sendUtbetaling()
        }

        @Test
        fun `utbetaler ikke dersom vi ikke kan lagre utbetalingen`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()
            val serviceAndMocks = ServiceAndMocks(
                sakOgSøknadsbehandling = Pair(sak, innvilgetTilAttestering),
                søknadsbehandlingRepo = mock {
                    on { hent(any()) } doReturn innvilgetTilAttestering
                    doNothing().whenever(it).lagre(any(), anyOrNull())
                },
                utbetalingService = mock {
                    on {
                        klargjørUtbetaling(
                            any(),
                            any(),
                        )
                    } doThrow RuntimeException("Mock: Kan ikke klargjøre utbetaling (som også persisterer)")
                    doAnswer { invocation ->
                        simulerUtbetaling(
                            sak,
                            invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering,
                            invocation.getArgument(1) as Periode,
                            TikkendeKlokke(fixedClock),
                        )
                    }.whenever(it).simulerUtbetaling(any(), any())
                },
                opprettPlanlagtKontrollsamtaleService = mock {},
                vedtakRepo = mock {
                    doNothing().whenever(it).lagreITransaksjon(any(), anyOrNull())
                },

            )

            shouldThrow<RuntimeException> {
                serviceAndMocks.service.iverksett(
                    IverksettSøknadsbehandlingCommand(
                        behandlingId = innvilgetTilAttestering.id,
                        attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                    ),
                )
            }.message shouldBe "Mock: Kan ikke klargjøre utbetaling (som også persisterer)"
        }

        @Test
        fun `utbetaler ikke dersom vi ikke kan lagre vedtaket`() {
            val (sak, innvilgetTilAttestering) = søknadsbehandlingTilAttesteringInnvilget()
            val utbetalingMedCallback = mock<UtbetalingKlargjortForOversendelse<UtbetalingFeilet.Protokollfeil>>()
            val serviceAndMocks = ServiceAndMocks(
                sakOgSøknadsbehandling = Pair(sak, innvilgetTilAttestering),
                utbetalingService = mock {
                    on { klargjørUtbetaling(any(), any()) } doReturn utbetalingMedCallback.right()
                    doAnswer { invocation ->
                        val utbetaling = invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering
                        simulerUtbetaling(
                            sak = sak,
                            utbetaling = utbetaling,
                            simuleringsperiode = invocation.getArgument(1) as Periode,
                            clock = fixedClock,
                        )
                    }.whenever(it).simulerUtbetaling(any(), any())
                },
                vedtakRepo = mock {
                    doThrow(RuntimeException("Mock: utbetaler ikke dersom vi ikke kan lagre vedtaket")).whenever(it)
                        .lagreITransaksjon(any(), anyOrNull())
                },
            )
            shouldThrow<RuntimeException> {
                serviceAndMocks.service.iverksett(
                    IverksettSøknadsbehandlingCommand(
                        behandlingId = innvilgetTilAttestering.id,
                        attestering = Attestering.Iverksatt(attestant, fixedTidspunkt),
                    ),
                )
            }.message shouldBe "Mock: utbetaler ikke dersom vi ikke kan lagre vedtaket"

            verify(utbetalingMedCallback, never()).sendUtbetaling()
        }
    }

    // TODO jah: Denne kan tilpasses og enables etter vi har åpnet for parallelle behandlinger.
    @Disabled("Dette er ikke et case vi kan provosere frem lenger, siden oppdaterStønadsperiode(...) vil stoppe oss.")
    @Test
    fun `feil ved åpent kravgrunnlag`() {
        val clock: Clock = TikkendeKlokke()
        val (sak, søknadsbehandling) = vedtakRevurdering(
            clock = clock,
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(arbeidsinntekt = 50000.0),
            ),
            utbetalingerKjørtTilOgMed = { 31.desember(2021) },
        ).let { (sak, _) ->
            tilAttesteringSøknadsbehandling(
                clock = clock,
                sakOgSøknad = Pair(
                    first = sak,
                    second = nySøknadJournalførtMedOppgave(
                        sakId = sak.id,
                        søknadInnhold = søknadinnholdUføre(personopplysninger = personopplysninger(sak.fnr)),
                    ),
                ),
            )
        }

        ServiceAndMocks(
            clock = clock,
            sakOgSøknadsbehandling = Pair(sak, søknadsbehandling),
        ).also {
            it.service.iverksett(
                IverksettSøknadsbehandlingCommand(
                    behandlingId = søknadsbehandling.id,
                    attestering = attesteringIverksatt(),
                ),
            ) shouldBe KunneIkkeIverksetteSøknadsbehandling.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving.left()
        }
    }
}

/**
 * @param sakOgSøknadsbehandling før-tilstand på sak inkl. søknadsbehandlingen som skal iverksettes.
 */
private data class ServiceAndMocks(
    val sakOgSøknadsbehandling: Pair<Sak, Søknadsbehandling>?,
    val søknadsbehandlingRepo: SøknadsbehandlingRepo = mock {
        if (sakOgSøknadsbehandling != null) {
            on { hent(any()) } doReturn sakOgSøknadsbehandling.second
        } else {
            on { hent(any()) } doReturn null
        }
        doNothing().whenever(it).lagre(any(), anyOrNull())
    },
    val utbetalingKlargjortForOversendelseCallback: (_: Utbetalingsrequest) -> Either<*, Utbetalingsrequest> = mock {
        on { it.invoke(any()) } doReturn utbetalingsRequest.right()
    },
    val clock: Clock = fixedClock,
    val utbetalingService: UtbetalingService = mock {

        if (sakOgSøknadsbehandling != null) {
            doAnswer { invocation ->
                val utbetaling = invocation.getArgument(0) as Utbetaling.UtbetalingForSimulering
                simulerUtbetaling(
                    sak = sakOgSøknadsbehandling.first,
                    utbetaling = utbetaling,
                    simuleringsperiode = invocation.getArgument(1) as Periode,
                    clock = clock,
                )
            }.whenever(it).simulerUtbetaling(any(), any())

            doAnswer { invocation ->
                val simulertUtbetaling = invocation.getArgument(0) as Utbetaling.SimulertUtbetaling

                val utbetaling = Utbetaling.OversendtUtbetaling.UtenKvittering(
                    simulertUtbetaling = simulertUtbetaling,
                    simulering = simulertUtbetaling.simulering,
                    utbetalingsrequest = utbetalingsRequest,
                )
                UtbetalingKlargjortForOversendelse(
                    utbetaling = utbetaling,
                    callback = utbetalingKlargjortForOversendelseCallback,
                ).right()
            }.whenever(it).klargjørUtbetaling(any(), any())
        } else {
            on {
                simulerUtbetaling(
                    any(),
                    any(),
                )
            } doThrow IllegalArgumentException("Kan ikke simulere utbetaling når sakOgSøknadsbehandling er null.")
            on {
                klargjørUtbetaling(
                    any(),
                    any(),
                )
            } doThrow IllegalArgumentException("Kan ikke klargjøre utbetaling når sakOgSøknadsbehandling er null.")
        }
    },
    val observer: StatistikkEventObserver = mock(),
    val brevService: BrevService = mock {
        on { lagDokument(any()) } doReturn dokumentUtenMetadataVedtak().right()
    },
    val vedtakRepo: VedtakRepo = mock {
        doNothing().whenever(it).lagreITransaksjon(any(), anyOrNull())
    },
    val ferdigstillVedtakService: FerdigstillVedtakService = mock { mock ->
        doAnswer {
            (it.arguments[0]).right()
        }.whenever(mock).lukkOppgaveMedBruker(any())
    },
    val sakService: SakService = mock {
        if (sakOgSøknadsbehandling != null) {
            on { hentSakForSøknadsbehandling(any()) } doReturn sakOgSøknadsbehandling.first
        } else {
            on { hentSakForSøknadsbehandling(any()) } doThrow NullPointerException("Kan ikke hente sak for søknadsbehandling id. Eksisterer IDen? Denne feilmeldingen er generert vha. en mock.")
        }
    },
    val opprettPlanlagtKontrollsamtaleService: OpprettKontrollsamtaleVedNyStønadsperiodeService = mock {},
    val sessionFactory: SessionFactory = TestSessionFactory(),
    val dokumentRepo: DokumentRepo = mock {},
    val skattDokumentService: SkattDokumentService = mock {},
) {
    val service = IverksettSøknadsbehandlingServiceImpl(
        sakService = sakService,
        clock = clock,
        utbetalingService = utbetalingService,
        sessionFactory = sessionFactory,
        søknadsbehandlingRepo = søknadsbehandlingRepo,
        vedtakRepo = vedtakRepo,
        opprettPlanlagtKontrollsamtaleService = opprettPlanlagtKontrollsamtaleService,
        ferdigstillVedtakService = ferdigstillVedtakService,
        brevService = brevService,
        skattDokumentService = skattDokumentService,
        satsFactory = satsFactoryTestPåDato(),
    ).apply { addObserver(observer) }

    fun allMocks(): Array<Any> {
        return listOf(
            søknadsbehandlingRepo,
            utbetalingService,
            observer,
            brevService,
            vedtakRepo,
            ferdigstillVedtakService,
            sakService,
            opprettPlanlagtKontrollsamtaleService,
        ).toTypedArray()
    }

    fun verifyNoMoreInteractions() {
        org.mockito.kotlin.verifyNoMoreInteractions(
            søknadsbehandlingRepo,
            utbetalingService,
            observer,
            brevService,
            vedtakRepo,
            ferdigstillVedtakService,
            sakService,
            opprettPlanlagtKontrollsamtaleService,
        )
    }
}
