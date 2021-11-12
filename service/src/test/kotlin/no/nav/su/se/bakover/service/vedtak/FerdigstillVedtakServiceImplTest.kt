package no.nav.su.se.bakover.service.vedtak

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.database.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.database.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.behandling.BehandlingMedOppgave
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.revurdering.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.BrevService
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.FantIkkeVedtakForUtbetalingId
import no.nav.su.se.bakover.service.vedtak.FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeGenerereBrev
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.oversendtUtbetalingMedKvittering
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattInnvilget
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattAvslagMedBeregning
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.time.Clock

internal class FerdigstillVedtakServiceImplTest {

    @Test
    fun `prøver ikke ferdigstille dersom kvittering er feil`() {
        FerdigstillVedtakServiceMocks {
            service.ferdigstillVedtakEtterUtbetaling(
                oversendtUtbetalingMedKvittering(
                    utbetalingsstatus = Kvittering.Utbetalingsstatus.FEIL,
                ),
            )
        }
    }

    @Test
    fun `prøver ikke å ferdigstille dersom utbetalingstype er gjennoppta`() {
        FerdigstillVedtakServiceMocks {
            service.ferdigstillVedtakEtterUtbetaling(
                oversendtUtbetalingMedKvittering(
                    utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                    type = Utbetaling.UtbetalingsType.GJENOPPTA,
                ),
            )
        }
    }

    @Test
    fun `prøver ikke å ferdigstille dersom utbetalingstype er stans`() {
        FerdigstillVedtakServiceMocks {
            service.ferdigstillVedtakEtterUtbetaling(
                oversendtUtbetalingMedKvittering(
                    utbetalingsstatus = Kvittering.Utbetalingsstatus.OK,
                    type = Utbetaling.UtbetalingsType.STANS,
                ),
            )
        }
    }

    @Test
    fun `ferdigstill NY ender i feil hvis utbetalinga ikke kan kobles til et vedtak`() {
        val (sak, vedtak) = innvilgetSøknadsbehandlingVedtak()

        FerdigstillVedtakServiceMocks(
            vedtakRepo = mock {
                on { hentForUtbetaling(any()) } doReturn null
            },
        ) {
            val feil = service.ferdigstillVedtakEtterUtbetaling(sak.utbetalinger.first() as Utbetaling.OversendtUtbetaling.MedKvittering)
            feil shouldBe FantIkkeVedtakForUtbetalingId(vedtak.utbetalingId).left()

            verify(vedtakRepo).hentForUtbetaling(vedtak.utbetalingId)
        }
    }

    @Test
    fun `ferdigstill NY kaster feil hvis man ikke finner person for generering av brev`() {
        val (sak, vedtak) = innvilgetSøknadsbehandlingVedtak()

        FerdigstillVedtakServiceMocks(
            vedtakRepo = mock {
                on { hentForUtbetaling(any()) } doReturn vedtak
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn KunneIkkeLageDokument.KunneIkkeHentePerson.left()
            }
        ) {
            val feil = service.ferdigstillVedtakEtterUtbetaling(sak.utbetalinger.first() as Utbetaling.OversendtUtbetaling.MedKvittering)
            feil shouldBe KunneIkkeGenerereBrev.left()

            verify(vedtakRepo).hentForUtbetaling(vedtak.utbetalingId)
            verify(brevService).lagDokument(vedtak)
        }
    }

    @Test
    fun `ferdigstillelse etter utbetaling kaster feil hvis generering av brev feiler`() {
        val (sak, vedtak) = innvilgetSøknadsbehandlingVedtak()

        FerdigstillVedtakServiceMocks(
            vedtakRepo = mock {
                on { hentForUtbetaling(any()) } doReturn vedtak
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn KunneIkkeLageDokument.KunneIkkeGenererePDF.left()
            }
        ) {
            val feil = service.ferdigstillVedtakEtterUtbetaling(sak.utbetalinger.first() as Utbetaling.OversendtUtbetaling.MedKvittering)
            feil shouldBe KunneIkkeGenerereBrev.left()

            inOrder(
                *all(),
            ) {
                verify(vedtakRepo).hentForUtbetaling(argThat { it shouldBe vedtak.utbetalingId })
                verify(brevService).lagDokument(argThat { it shouldBe vedtak })
            }
        }
    }

    @Test
    fun `ferdigstill NY etter utbetaling går fint`() {
        val (sak, vedtak) = innvilgetSøknadsbehandlingVedtak()

        FerdigstillVedtakServiceMocks(
            oppgaveService = mock {
                on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
            },
            vedtakRepo = mock {
                on { hentForUtbetaling(any()) } doReturn vedtak
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn Dokument.UtenMetadata.Vedtak(
                    opprettet = fixedTidspunkt,
                    tittel = "tittel1",
                    generertDokument = "brev".toByteArray(),
                    generertDokumentJson = "brev",
                ).right()
            }
        ) {
            service.ferdigstillVedtakEtterUtbetaling(sak.utbetalinger.first() as Utbetaling.OversendtUtbetaling.MedKvittering)

            inOrder(
                *all(),
            ) {
                verify(vedtakRepo).hentForUtbetaling(argThat { it shouldBe vedtak.utbetalingId })
                verify(brevService).lagDokument(argThat { it shouldBe vedtak })
                verify(brevService).lagreDokument(
                    argThat {
                        it.generertDokument contentEquals "brev".toByteArray()
                        it.metadata shouldBe Dokument.Metadata(
                            sakId = sak.id,
                            vedtakId = vedtak.id,
                            bestillBrev = true,
                        )
                    },
                )
                verify(oppgaveService).lukkOppgaveMedSystembruker(argThat { it shouldBe (vedtak.behandling as BehandlingMedOppgave).oppgaveId })
                verify(behandlingMetrics).incrementInnvilgetCounter(argThat { it shouldBe BehandlingMetrics.InnvilgetHandlinger.LUKKET_OPPGAVE })
            }
        }
    }

    @Test
    fun `ferdigstill NY av regulering av grunnbeløp etter utbetaling skal ikke sende brev men skal lukke oppgave`() {
        val (sak, vedtak) = vedtakRevurderingIverksattInnvilget(
            sakOgVedtakSomKanRevurderes = innvilgetSøknadsbehandlingVedtak(),
            revurderingsårsak = Revurderingsårsak(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP,
                Revurderingsårsak.Begrunnelse.create("Regulering av grunnbeløpet påvirket ytelsen."),
            ),
        )

        FerdigstillVedtakServiceMocks(
            oppgaveService = mock {
                on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
            },
            vedtakRepo = mock {
                on { hentForUtbetaling(any()) } doReturn vedtak
            },
        ) {
            service.ferdigstillVedtakEtterUtbetaling(sak.utbetalinger[1] as Utbetaling.OversendtUtbetaling.MedKvittering)

            inOrder(
                *all(),
            ) {
                verify(vedtakRepo).hentForUtbetaling(vedtak.utbetalingId)
                verify(oppgaveService).lukkOppgaveMedSystembruker((vedtak.behandling as BehandlingMedOppgave).oppgaveId)
            }
        }
    }

    @Test
    fun `svarer med feil dersom lukking av oppgave feiler`() {
        val vedtak = avslagsVedtak()

        FerdigstillVedtakServiceMocks(
            oppgaveService = mock {
                on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
            },
        ) {
            service.lukkOppgaveMedBruker(vedtak) shouldBe FerdigstillVedtakService.KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave.left()

            inOrder(
                *all(),
            ) {
                verify(oppgaveService).lukkOppgave(vedtak.behandling.oppgaveId)
            }
        }
    }

    internal data class FerdigstillVedtakServiceMocks(
        val oppgaveService: OppgaveService = mock(),
        val clock: Clock = fixedClock,
        val brevService: BrevService = mock(),
        val vedtakRepo: VedtakRepo = mock(),
        val utbetalingRepo: UtbetalingRepo = mock(),
        val behandlingMetrics: BehandlingMetrics = mock(),
        val runTest: FerdigstillVedtakServiceMocks.() -> Unit,
    ) {
        val service = FerdigstillVedtakServiceImpl(
            brevService = brevService,
            oppgaveService = oppgaveService,
            vedtakRepo = vedtakRepo,
            behandlingMetrics = behandlingMetrics,
        )

        init {
            runTest()
            verifyNoMoreInteractions()
        }

        fun all() = listOf(
            oppgaveService,
            brevService,
            vedtakRepo,
            utbetalingRepo,
            behandlingMetrics,
        ).toTypedArray()

        private fun verifyNoMoreInteractions() {
            org.mockito.kotlin.verifyNoMoreInteractions(
                *all(),
            )
        }
    }

    private fun avslagsVedtak(): Vedtak.Avslag.AvslagBeregning =
        vedtakSøknadsbehandlingIverksattAvslagMedBeregning().second

    private fun innvilgetSøknadsbehandlingVedtak(): Pair<Sak, Vedtak.EndringIYtelse> {
        return vedtakSøknadsbehandlingIverksattInnvilget()
    }
}
