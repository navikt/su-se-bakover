package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.BeregnetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.vedtak.Vedtak
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.attestant
import no.nav.su.se.bakover.test.attesteringUnderkjent
import no.nav.su.se.bakover.test.beregnetRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock

class RevurderingIngenEndringTest {

    @Test
    fun `ingen endring dersom forskjell mellom eksisterende utbetalt beløp og nytt beløp er mindre enn 10 prosent`() {
        val (sak, revurdering) = opprettetRevurdering()

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurdering
            },
            utbetalingService = mock {
                on { hentUtbetalinger(any()) } doReturn sak.utbetalinger
            },
            vedtakService = mock {
                on {
                    kopierGjeldendeVedtaksdata(
                        any(),
                        any(),
                    )
                } doReturn sak.kopierGjeldendeVedtaksdata(revurdering.periode.fraOgMed, fixedClock).getOrFail().right()
            },
        ).let {
            val actual = it.revurderingService.beregnOgSimuler(
                revurderingId = revurderingId,
                saksbehandler = no.nav.su.se.bakover.test.saksbehandler,
            ).getOrFail()
            actual.revurdering shouldBe beOfType<BeregnetRevurdering.IngenEndring>()
            (actual.revurdering as BeregnetRevurdering.IngenEndring).let { ingenEndring ->
                ingenEndring.saksbehandler shouldBe no.nav.su.se.bakover.test.saksbehandler
                ingenEndring.grunnlagsdata shouldBe revurdering.grunnlagsdata
                ingenEndring.vilkårsvurderinger shouldBe revurdering.vilkårsvurderinger
                ingenEndring.beregning.getSumYtelse() shouldBe (
                    sak.utbetalinger.flatMap { it.utbetalingslinjer }
                        .fold(0) { acc, utbetalingslinje ->
                            acc + utbetalingslinje.beløp * utbetalingslinje.periode.getAntallMåneder()
                        }
                    )
            }

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(argThat { it shouldBe revurderingId })
                verify(it.utbetalingService).hentUtbetalinger(argThat { it shouldBe revurdering.sakId })
                verify(it.vedtakService).kopierGjeldendeVedtaksdata(
                    argThat { it shouldBe revurdering.sakId },
                    argThat { it shouldBe revurdering.periode.fraOgMed },
                )
                verify(it.revurderingRepo).lagre(argThat { it shouldBe actual.revurdering })
                verify(it.grunnlagService).lagreFradragsgrunnlag(
                    argThat { it shouldBe revurderingId },
                    argThat { it shouldBe actual.revurdering.grunnlagsdata.fradragsgrunnlag },
                )
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `attesterer revurdering som ikke fører til endring i ytelse`() {
        val endretSaksbehandler = NavIdentBruker.Saksbehandler("endretSaksbehandler")

        val beregnetRevurdering = beregnetRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak().second

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn beregnetRevurdering
                on { hentEventuellTidligereAttestering(any()) } doReturn null
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn oppgaveIdRevurdering.right()
                on { lukkOppgave(any()) } doReturn Unit.right()
            },
        ).let { serviceAndMocks ->
            val actual = serviceAndMocks.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = beregnetRevurdering.id,
                    saksbehandler = endretSaksbehandler,
                    fritekstTilBrev = "endret fritekst",
                    skalFøreTilBrevutsending = true,
                ),
            ).getOrFail()

            (actual as RevurderingTilAttestering.IngenEndring).let {
                inOrder(
                    *serviceAndMocks.all(),
                ) {
                    actual.saksbehandler shouldBe endretSaksbehandler

                    verify(serviceAndMocks.revurderingRepo).hent(argThat { it shouldBe beregnetRevurdering.id })
                    verify(serviceAndMocks.personService).hentAktørId(argThat { it shouldBe fnr })
                    verify(serviceAndMocks.revurderingRepo).hentEventuellTidligereAttestering(argThat { it shouldBe beregnetRevurdering.id })
                    verify(serviceAndMocks.oppgaveService).opprettOppgave(
                        argThat {
                            it shouldBe OppgaveConfig.AttesterRevurdering(
                                saksnummer = beregnetRevurdering.saksnummer,
                                aktørId = aktørId,
                            )
                        },
                    )
                    verify(serviceAndMocks.oppgaveService).lukkOppgave(argThat { it shouldBe oppgaveIdRevurdering })
                    verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe actual })
                }
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `underkjenn revurdering`() {
        val tilAttestering = tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak().second

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn tilAttestering
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn oppgaveIdRevurdering.right()
                on { lukkOppgave(any()) } doReturn Unit.right()
            },
        ).let { serviceAndMocks ->
            val actual = serviceAndMocks.revurderingService.underkjenn(
                revurderingId = tilAttestering.id,
                attestering = attesteringUnderkjent(clock = fixedClock),
            ).getOrFail()

            (actual as UnderkjentRevurdering.IngenEndring).let {
                inOrder(
                    *serviceAndMocks.all(),
                ) {
                    tilAttestering.oppgaveId shouldBe OppgaveId("oppgaveid")
                    actual.oppgaveId shouldBe oppgaveIdRevurdering

                    verify(serviceAndMocks.revurderingRepo).hent(argThat { it shouldBe tilAttestering.id })
                    verify(serviceAndMocks.personService).hentAktørId(argThat { it shouldBe fnr })
                    verify(serviceAndMocks.oppgaveService).opprettOppgave(
                        argThat {
                            it shouldBe OppgaveConfig.Revurderingsbehandling(
                                saksnummer = saksnummer,
                                aktørId = aktørId,
                                tilordnetRessurs = no.nav.su.se.bakover.test.saksbehandler,
                            )
                        },
                    )
                    verify(serviceAndMocks.revurderingRepo).lagre(argThat { it shouldBe actual })
                    verify(serviceAndMocks.oppgaveService).lukkOppgave(argThat { it shouldBe OppgaveId("oppgaveid") })
                }
                serviceAndMocks.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `iverksetter revurdering som ikke fører til endring i ytelse og sender brev`() {
        val (sak, revurderingTilAttestering) = tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
            skalFøreTilBrevutsending = true,
        )

        val iverksattRevurdering = revurderingTilAttestering.tilIverksatt(attestant, fixedClock).orNull()!!
        val vedtak = Vedtak.from(iverksattRevurdering, fixedClock)

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn Dokument.UtenMetadata.Vedtak(
                    opprettet = fixedTidspunkt,
                    tittel = "tittel1",
                    generertDokument = "brev".toByteArray(),
                    generertDokumentJson = "brev",
                ).right()
            },
            utbetalingService = mock {
                on {
                    hentGjeldendeUtbetaling(
                        any(),
                        any(),
                    )
                } doReturn sak.utbetalingstidslinje(revurderingTilAttestering.periode)
                    .gjeldendeForDato(revurderingTilAttestering.periode.fraOgMed)!!.right()
            },
        ).let {
            it.revurderingService.iverksett(
                revurderingId = revurderingId,
                attestant = attestant,
            ) shouldBe iverksattRevurdering.right()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(revurderingTilAttestering.id)
                verify(it.brevService).lagDokument(argThat { it shouldBe beOfType<Vedtak.IngenEndringIYtelse>() })
                verify(it.vedtakRepo).lagre(argThat { it shouldBe vedtak.copy(id = it.id) })
                verify(it.brevService).lagreDokument(
                    argThat {
                        it shouldBe beOfType<Dokument.MedMetadata.Vedtak>()
                        it.generertDokument shouldNotBe null
                        it.generertDokumentJson shouldNotBe null
                        it.metadata.sakId shouldBe sak.id
                        it.metadata.vedtakId shouldNotBe null
                        it.metadata.bestillBrev shouldBe true
                    },
                )
                verify(it.revurderingRepo).lagre(iverksattRevurdering)

                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `iverksetter revurdering som ikke fører til endring i ytelse og sender ikke brev`() {
        val (_, revurderingTilAttestering) = tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
            skalFøreTilBrevutsending = false,
        )

        val iverksattRevurdering = revurderingTilAttestering.tilIverksatt(attestant, fixedClock).orNull()!!
        val vedtak = Vedtak.from(iverksattRevurdering, fixedClock)

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(any()) } doReturn revurderingTilAttestering
        }

        val serviceAndMocks = RevurderingServiceMocks(
            revurderingRepo = revurderingRepoMock,
        )

        serviceAndMocks.revurderingService.iverksett(
            revurderingId,
            attestant,
        ) shouldBe iverksattRevurdering.right()

        inOrder(
            *serviceAndMocks.all(),
        ) {
            verify(revurderingRepoMock).hent(revurderingTilAttestering.id)
            verify(serviceAndMocks.vedtakRepo).lagre(argThat { it shouldBe vedtak.copy(id = it.id) })
            verify(revurderingRepoMock).lagre(any())
            verifyNoMoreInteractions()
        }
    }

    @Test
    fun `iverksetter revurdering som ikke fører til endring i ytelse svarer med feil hvis vi ikke kan hente person`() {
        val (_, revurderingTilAttestering) = tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
            skalFøreTilBrevutsending = true,
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn KunneIkkeLageDokument.KunneIkkeHentePerson.left()
            },
        ).let {

            it.revurderingService.iverksett(
                revurderingId,
                attestant,
            ) shouldBe KunneIkkeIverksetteRevurdering.FantIkkePerson.left()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(revurderingTilAttestering.id)
                verify(it.brevService).lagDokument(any())
                verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `iverksetter revurdering som ikke fører til endring i ytelse svarer med feil hvis vi ikke kan hente gjeldende utbetaling`() {
        val (_, revurderingTilAttestering) = tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
            skalFøreTilBrevutsending = true,
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn KunneIkkeLageDokument.KunneIkkeFinneGjeldendeUtbetaling.left()
            },
        ).let {
            it.revurderingService.iverksett(
                revurderingId,
                attestant,
            ) shouldBe KunneIkkeIverksetteRevurdering.KunneIkkeFinneGjeldendeUtbetaling.left()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(revurderingTilAttestering.id)
                verify(it.brevService).lagDokument(argThat { it shouldBe beOfType<Vedtak.IngenEndringIYtelse>() })
                verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `iverksetter revurdering som ikke fører til endring i ytelse svarer med feil hvis generering av brev feiler`() {
        val (_, revurderingTilAttestering) = tilAttesteringRevurderingIngenEndringFraInnvilgetSøknadsbehandlingsVedtak(
            skalFøreTilBrevutsending = true,
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurderingTilAttestering
            },
            brevService = mock {
                on { lagDokument(any()) } doReturn KunneIkkeLageDokument.KunneIkkeGenererePDF.left()
            },
        ).let {
            it.revurderingService.iverksett(
                revurderingId,
                attestant,
            ) shouldBe KunneIkkeIverksetteRevurdering.KunneIkkeGenerereBrev.left()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(revurderingTilAttestering.id)
                verify(it.brevService).lagDokument(argThat { it shouldBe beOfType<Vedtak.IngenEndringIYtelse>() })
                verifyNoMoreInteractions()
            }
        }
    }
}
