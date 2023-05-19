package no.nav.su.se.bakover.service.vedtak

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.tid.periode.desember
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.domain.behandling.BehandlingMedOppgave
import no.nav.su.se.bakover.domain.behandling.BehandlingMetrics
import no.nav.su.se.bakover.domain.brev.BrevService
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.oppdrag.Kvittering
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppdrag.utbetaling.UtbetalingRepo
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil.KunneIkkeLukkeOppgave
import no.nav.su.se.bakover.domain.oppgave.OppgaveService
import no.nav.su.se.bakover.domain.revurdering.årsak.Revurderingsårsak
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeFerdigstilleVedtak
import no.nav.su.se.bakover.domain.vedtak.KunneIkkeFerdigstilleVedtakMedUtbetaling
import no.nav.su.se.bakover.domain.vedtak.VedtakRepo
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.ikkeSendBrev
import no.nav.su.se.bakover.test.shouldBeType
import no.nav.su.se.bakover.test.søknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.utbetaling.oversendtUtbetalingMedKvittering
import no.nav.su.se.bakover.test.vedtakIverksattGjenopptakAvYtelseFraIverksattStans
import no.nav.su.se.bakover.test.vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak
import no.nav.su.se.bakover.test.vedtakRevurderingIverksattInnvilget
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import java.time.Clock

internal class FerdigstillVedtakServiceImplTest {

    @Test
    fun `prøver ikke ferdigstille dersom kvittering er feil`() {
        FerdigstillVedtakServiceMocks {
            service.ferdigstillVedtakEtterUtbetaling(
                oversendtUtbetalingMedKvittering(
                    utbetalingsstatus = Kvittering.Utbetalingsstatus.FEIL,
                ),
            ) shouldBe Unit.right()
            verifyNoInteractions(vedtakRepo)
        }
    }

    @Test
    fun `prøver ikke å ferdigstille dersom utbetalingstype er gjennoppta`() {
        FerdigstillVedtakServiceMocks {
            service.ferdigstillVedtakEtterUtbetaling(
                vedtakIverksattGjenopptakAvYtelseFraIverksattStans(
                    periode = februar(2021).rangeTo(desember(2021)),
                ).let { (sak, vedtak) ->
                    val utbetaling =
                        sak.utbetalinger.single { it.id == vedtak.utbetalingId } as Utbetaling.OversendtUtbetaling.MedKvittering
                    assert(utbetaling.erReaktivering())
                    utbetaling
                },
            ) shouldBe Unit.right()
            verifyNoInteractions(vedtakRepo)
        }
    }

    @Test
    fun `prøver ikke å ferdigstille dersom utbetalingstype er stans`() {
        FerdigstillVedtakServiceMocks {
            service.ferdigstillVedtakEtterUtbetaling(
                vedtakIverksattStansAvYtelseFraIverksattSøknadsbehandlingsvedtak().let { (sak, vedtak) ->
                    val utbetaling =
                        sak.utbetalinger.single { it.id == vedtak.utbetalingId } as Utbetaling.OversendtUtbetaling.MedKvittering
                    assert(utbetaling.erStans())
                    utbetaling
                },
            ) shouldBe Unit.right()
            verifyNoInteractions(vedtakRepo)
        }
    }

    @Test
    fun `ferdigstill NY ender i feil hvis utbetalinga ikke kan kobles til et vedtak`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()

        FerdigstillVedtakServiceMocks(
            vedtakRepo = mock {
                on { hentForUtbetaling(any()) } doReturn null
            },
        ) {
            val utbetaling =
                (sak.utbetalinger.first() as Utbetaling.OversendtUtbetaling.MedKvittering)
            val feil =
                service.ferdigstillVedtakEtterUtbetaling(utbetaling)
            feil shouldBe KunneIkkeFerdigstilleVedtakMedUtbetaling.FantIkkeVedtakForUtbetalingId(vedtak.utbetalingId).left()

            verify(vedtakRepo).hentForUtbetaling(vedtak.utbetalingId)
        }
    }

    @Test
    fun `ferdigstill NY kaster feil hvis man ikke finner person for generering av brev`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()

        FerdigstillVedtakServiceMocks(
            vedtakRepo = mock {
                on { hentForUtbetaling(any()) } doReturn vedtak
            },
            brevService = mock {
                on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn KunneIkkeLageDokument.KunneIkkeHentePerson.left()
            },
        ) {
            val utbetaling = sak.utbetalinger.first() as Utbetaling.OversendtUtbetaling.MedKvittering
            val feil =
                service.ferdigstillVedtakEtterUtbetaling(utbetaling)
            feil shouldBe KunneIkkeFerdigstilleVedtak.KunneIkkeGenerereBrev(KunneIkkeLageDokument.KunneIkkeHentePerson)
                .left()

            verify(vedtakRepo).hentForUtbetaling(vedtak.utbetalingId)
            verify(brevService).lagDokument(vedtak)
        }
    }

    @Test
    fun `ferdigstillelse etter utbetaling kaster feil hvis generering av brev feiler`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()

        FerdigstillVedtakServiceMocks(
            vedtakRepo = mock {
                on { hentForUtbetaling(any()) } doReturn vedtak
            },
            brevService = mock {
                on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn KunneIkkeLageDokument.KunneIkkeGenererePDF.left()
            },
        ) {
            val utbetaling =
                (sak.utbetalinger.first() as Utbetaling.OversendtUtbetaling.MedKvittering)
            val feil =
                service.ferdigstillVedtakEtterUtbetaling(utbetaling)
            feil shouldBe KunneIkkeFerdigstilleVedtak.KunneIkkeGenerereBrev(KunneIkkeLageDokument.KunneIkkeGenererePDF)
                .left()

            inOrder(
                *all(),
            ) {
                verify(vedtakRepo).hentForUtbetaling(argThat { it shouldBe vedtak.utbetalingId })
                verify(brevService).lagDokument(argThat<Visitable<LagBrevRequestVisitor>> { it shouldBe vedtak })
            }
        }
    }

    @Test
    fun `ferdigstill NY etter utbetaling går fint`() {
        val (sak, vedtak) = vedtakSøknadsbehandlingIverksattInnvilget()

        FerdigstillVedtakServiceMocks(
            oppgaveService = mock {
                on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
            },
            vedtakRepo = mock {
                on { hentForUtbetaling(any()) } doReturn vedtak
            },
            brevService = mock {
                on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn Dokument.UtenMetadata.Vedtak(
                    opprettet = fixedTidspunkt,
                    tittel = "tittel1",
                    generertDokument = "brev".toByteArray(),
                    generertDokumentJson = "brev",
                ).right()
            },
        ) {
            val utbetaling =
                sak.utbetalinger.first() as Utbetaling.OversendtUtbetaling.MedKvittering
            service.ferdigstillVedtakEtterUtbetaling(utbetaling)

            inOrder(
                *all(),
            ) {
                verify(vedtakRepo).hentForUtbetaling(argThat { it shouldBe vedtak.utbetalingId })
                verify(brevService).lagDokument(argThat<Visitable<LagBrevRequestVisitor>> { it shouldBe vedtak })
                verify(brevService).lagreDokument(
                    argThat {
                        it.generertDokument contentEquals "brev".toByteArray()
                        it.metadata shouldBe Dokument.Metadata(sakId = sak.id, vedtakId = vedtak.id)
                    },
                )
                verify(oppgaveService).lukkOppgaveMedSystembruker(argThat { it shouldBe (vedtak.behandling as BehandlingMedOppgave).oppgaveId })
                verify(behandlingMetrics).incrementInnvilgetCounter(argThat { it shouldBe BehandlingMetrics.InnvilgetHandlinger.LUKKET_OPPGAVE })
            }
        }
    }

    @Test
    fun `ferdigstill NY av regulering av grunnbeløp etter utbetaling skal ikke sende brev men skal lukke oppgave`() {
        val clock = TikkendeKlokke()
        val (sak, vedtak) = vedtakRevurderingIverksattInnvilget(
            sakOgVedtakSomKanRevurderes = vedtakSøknadsbehandlingIverksattInnvilget(
                clock = clock,
            ),
            revurderingsårsak = Revurderingsårsak(
                årsak = Revurderingsårsak.Årsak.REGULER_GRUNNBELØP,
                Revurderingsårsak.Begrunnelse.create("Regulering av grunnbeløpet påvirket ytelsen."),
            ),
            brevvalg = ikkeSendBrev(),
            clock = clock,
        )

        FerdigstillVedtakServiceMocks(
            oppgaveService = mock {
                on { lukkOppgaveMedSystembruker(any()) } doReturn Unit.right()
            },
            vedtakRepo = mock {
                on { hentForUtbetaling(any()) } doReturn vedtak
            },
            clock = clock,
        ) {
            service.ferdigstillVedtakEtterUtbetaling(
                sak.utbetalinger.single { it.id == vedtak.utbetalingId }
                    .shouldBeType<Utbetaling.OversendtUtbetaling.MedKvittering>(),
            )

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
        val behandling = søknadsbehandlingIverksattInnvilget().second

        FerdigstillVedtakServiceMocks(
            oppgaveService = mock {
                on { lukkOppgave(any()) } doReturn KunneIkkeLukkeOppgave.left()
            },
        ) {
            service.lukkOppgaveMedBruker(behandling) shouldBe KunneIkkeFerdigstilleVedtak.KunneIkkeLukkeOppgave.left()

            inOrder(
                *all(),
            ) {
                verify(oppgaveService).lukkOppgave(behandling.oppgaveId)
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
}
