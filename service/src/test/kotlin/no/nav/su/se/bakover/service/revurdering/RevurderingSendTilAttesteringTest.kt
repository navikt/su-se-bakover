package no.nav.su.se.bakover.service.revurdering

import arrow.core.Nel
import arrow.core.getOrHandle
import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.endOfMonth
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.grunnlag.singleFullstendigOrThrow
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.AvventerKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrev
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.Forhåndsvarsel
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.vilkår.Vilkår
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.oppgave.OppgaveService
import no.nav.su.se.bakover.service.person.PersonService
import no.nav.su.se.bakover.service.statistikk.Event
import no.nav.su.se.bakover.service.statistikk.EventObserver
import no.nav.su.se.bakover.service.toggles.ToggleService
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.createFromGrunnlag
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlag.formueGrunnlagUtenEps0Innvilget
import no.nav.su.se.bakover.test.grunnlag.formueGrunnlagUtenEpsAvslått
import no.nav.su.se.bakover.test.grunnlagsdataEnsligMedFradrag
import no.nav.su.se.bakover.test.grunnlagsdataEnsligUtenFradrag
import no.nav.su.se.bakover.test.oppgaveIdRevurdering
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vilkårsvurderingerAvslåttUføreOgAndreInnvilget
import no.nav.su.se.bakover.test.vilkårsvurderingerRevurderingInnvilget
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.time.temporal.ChronoUnit
import java.util.UUID

internal class RevurderingSendTilAttesteringTest {

    @Test
    fun `sender til attestering`() {
        val simulertRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            stønadsperiode = stønadsperiode2021,
            revurderingsperiode = Periode.create(fraOgMed = 1.juli(2021), tilOgMed = 30.september(2021)),
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
        ).second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurdering
        }
        val personServiceMock = mock<PersonService> {
            on { hentAktørId(any()) } doReturn aktørId.right()
        }
        val oppgaveServiceMock = mock<OppgaveService> {
            on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
            on { lukkOppgave(any()) } doReturn Unit.right()
        }

        val eventObserver: EventObserver = mock()

        val revurderingService = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            personService = personServiceMock,
            oppgaveService = oppgaveServiceMock,
        ).apply { addObserver(eventObserver) }

        val actual = revurderingService.sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        ).getOrHandle { throw RuntimeException(it.toString()) }

        inOrder(revurderingRepoMock, personServiceMock, oppgaveServiceMock, eventObserver) {
            verify(revurderingRepoMock).hent(argThat { it shouldBe revurderingId })
            verify(personServiceMock).hentAktørId(argThat { it shouldBe fnr })
            verify(oppgaveServiceMock).opprettOppgave(
                argThat {
                    it shouldBe OppgaveConfig.AttesterRevurdering(
                        saksnummer = saksnummer,
                        aktørId = aktørId,
                        tilordnetRessurs = null,
                        clock = fixedClock,
                    )
                },
            )
            verify(oppgaveServiceMock).lukkOppgave(argThat { it shouldBe simulertRevurdering.oppgaveId })
            verify(revurderingRepoMock).defaultTransactionContext()
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual }, anyOrNull())
            verify(eventObserver).handle(
                argThat {
                    it shouldBe Event.Statistikk.RevurderingStatistikk.RevurderingTilAttestering(
                        actual as RevurderingTilAttestering,
                    )
                },
            )
        }

        verifyNoMoreInteractions(revurderingRepoMock, personServiceMock, oppgaveServiceMock)
    }

    @Test
    fun `sender ikke til attestering hvis revurdering er ikke simulert`() {
        val opprettetRevurdering = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak().second

        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn opprettetRevurdering
        }

        val result = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        )

        result shouldBe KunneIkkeSendeRevurderingTilAttestering.UgyldigTilstand(
            OpprettetRevurdering::class,
            RevurderingTilAttestering::class,
        ).left()

        verify(revurderingRepoMock).hent(revurderingId)
        verifyNoMoreInteractions(revurderingRepoMock)
    }

    @Test
    fun `sender ikke til attestering hvis henting av aktørId feiler`() {
        val revurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            stønadsperiode = stønadsperiode2021,
            revurderingsperiode = Periode.create(fraOgMed = 1.juli(2021), tilOgMed = 30.september(2021)),
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
        ).second

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurdering.id) } doReturn revurdering
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        ).let { mocks ->
            val actual = mocks.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = revurdering.id,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "Fritekst",
                    skalFøreTilBrevutsending = true,
                ),
            )

            actual shouldBe KunneIkkeSendeRevurderingTilAttestering.FantIkkeAktørId.left()

            inOrder(*mocks.all()) {
                verify(mocks.revurderingRepo).hent(argThat { it shouldBe revurdering.id })
                verify(mocks.tilbakekrevingService).hentAvventerKravgrunnlag(any<UUID>())
                verify(mocks.personService).hentAktørId(argThat { it shouldBe revurdering.fnr })
                mocks.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `sender ikke til attestering hvis oppretting av oppgave feiler`() {
        val revurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak(
            stønadsperiode = stønadsperiode2021,
            revurderingsperiode = Periode.create(fraOgMed = 1.juli(2021), tilOgMed = 30.september(2021)),
            forhåndsvarsel = Forhåndsvarsel.Ferdigbehandlet.SkalIkkeForhåndsvarsles,
        ).second

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn revurdering
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveFeil.KunneIkkeOppretteOppgave.left()
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        ).let { mocks ->
            val actual = mocks.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = revurderingId,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "Fritekst",
                    skalFøreTilBrevutsending = true,
                ),
            )

            actual shouldBe KunneIkkeSendeRevurderingTilAttestering.KunneIkkeOppretteOppgave.left()

            inOrder(
                *mocks.all(),
            ) {
                verify(mocks.revurderingRepo).hent(revurderingId)
                verify(mocks.tilbakekrevingService).hentAvventerKravgrunnlag(any<UUID>())
                verify(mocks.personService).hentAktørId(argThat { it shouldBe fnr })
                verify(mocks.oppgaveService).opprettOppgave(
                    argThat {
                        it shouldBe
                            OppgaveConfig.AttesterRevurdering(
                                saksnummer = revurdering.saksnummer,
                                aktørId = aktørId,
                                tilordnetRessurs = null,
                                clock = fixedClock,
                            )
                    },
                )

                mocks.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `kan ikke sende revurdering med simulert feilutbetaling til attestering`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering(
                    grunnlagsdataOverrides = listOf(
                        fradragsgrunnlagArbeidsinntekt(
                            periode = år(2021),
                            arbeidsinntekt = 5000.0,
                        ),
                    ),
                ).second
            },
            toggleService = mock {
                on { isEnabled(any()) } doReturn false
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        ).let {
            it.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = revurderingId,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "Fritekst",
                    skalFøreTilBrevutsending = true,
                ),
            ) shouldBe KunneIkkeSendeRevurderingTilAttestering.FeilutbetalingStøttesIkke.left()

            verify(it.revurderingRepo, never()).lagre(any(), anyOrNull())
            verify(it.toggleService).isEnabled(ToggleService.toggleForFeilutbetaling)
        }
    }

    @Test
    fun `kan sende revurdering til attestering dersom toggle for feilbetaling tillatt er på`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering(
                    grunnlagsdataOverrides = listOf(
                        fradragsgrunnlagArbeidsinntekt(
                            periode = år(2021),
                            arbeidsinntekt = 5000.0,
                        ),
                    ),
                ).second
            },
            toggleService = mock {
                on { isEnabled(any()) } doReturn true
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn oppgaveIdRevurdering.right()
                on { lukkOppgave(any()) } doReturn Unit.right()
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        ).let {
            it.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = revurderingId,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "Fritekst",
                    skalFøreTilBrevutsending = true,
                ),
            ).getOrFail() shouldBe beOfType<RevurderingTilAttestering.Innvilget>()

            verify(it.revurderingRepo).lagre(any(), anyOrNull())
            verify(it.toggleService).isEnabled(ToggleService.toggleForFeilutbetaling)
        }
    }

    @Test
    fun `får ikke sende til attestering dersom tilbakekreving ikke er ferdigbehandlet`() {
        val revurdering = simulertRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 5000.0,
                ),
            ),
        ).second

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurdering.oppdaterTilbakekrevingsbehandling(
                    IkkeAvgjort(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        sakId = revurdering.sakId,
                        revurderingId = revurdering.id,
                        periode = revurdering.periode,
                    ),
                )
            },
            toggleService = mock {
                on { isEnabled(any()) } doReturn true
            },
            // TODO må endre rekkefølge slik at disse ikke kalles
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            // TODO må endre rekkefølge slik at disse ikke kalles
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn oppgaveIdRevurdering.right()
                on { lukkOppgave(any()) } doReturn Unit.right()
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        ).let {
            it.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = revurderingId,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "Fritekst",
                    skalFøreTilBrevutsending = true,
                ),
            ) shouldBe KunneIkkeSendeRevurderingTilAttestering.TilbakekrevingsbehandlingErIkkeFullstendig.left()
        }
    }

    @Test
    fun `får sende til attestering dersom tilbakekreving er ferdigbehandlet`() {
        val revurdering = simulertRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 5000.0,
                ),
            ),
        ).second

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurdering.oppdaterTilbakekrevingsbehandling(
                    IkkeAvgjort(
                        id = UUID.randomUUID(),
                        opprettet = fixedTidspunkt,
                        sakId = revurdering.sakId,
                        revurderingId = revurdering.id,
                        periode = revurdering.periode,
                    ).tilbakekrev(),
                )
            },
            toggleService = mock {
                on { isEnabled(any()) } doReturn true
            },
            // TODO må endre rekkefølge slik at disse ikke kalles
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            // TODO må endre rekkefølge slik at disse ikke kalles
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn oppgaveIdRevurdering.right()
                on { lukkOppgave(any()) } doReturn Unit.right()
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        ).let {
            it.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = revurderingId,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "Fritekst",
                    skalFøreTilBrevutsending = true,
                ),
            ).getOrFail() shouldBe beOfType<RevurderingTilAttestering.Innvilget>()
        }
    }

    @Test
    fun `formueopphør må være fra første måned`() {
        val stønadsperiode = RevurderingTestUtils.stønadsperiodeNesteMånedOgTreMånederFram
        val revurderingsperiode = stønadsperiode.periode
        val revurderingRepoMock = mock<RevurderingRepo> {
            val førsteUførevurderingsperiode = Periode.create(
                fraOgMed = revurderingsperiode.fraOgMed,
                tilOgMed = revurderingsperiode.fraOgMed.endOfMonth(),
            )
            val andreUførevurderingsperiode = Periode.create(
                fraOgMed = revurderingsperiode.fraOgMed.plus(1, ChronoUnit.MONTHS),
                tilOgMed = revurderingsperiode.tilOgMed,
            )

            val vilkårsvurderinger = vilkårsvurderingerRevurderingInnvilget(
                periode = revurderingsperiode,
                formue = Vilkår.Formue.Vurdert.createFromGrunnlag(
                    grunnlag = nonEmptyListOf(
                        formueGrunnlagUtenEps0Innvilget(
                            periode = førsteUførevurderingsperiode,
                            bosituasjon = Nel.fromListUnsafe(
                                grunnlagsdataEnsligUtenFradrag(
                                    periode = førsteUførevurderingsperiode,
                                ).bosituasjon.map { it as Grunnlag.Bosituasjon.Fullstendig },
                            ),
                        ),
                        formueGrunnlagUtenEpsAvslått(
                            periode = andreUførevurderingsperiode,
                            bosituasjon = grunnlagsdataEnsligUtenFradrag(
                                periode = andreUførevurderingsperiode,
                            ).bosituasjon.singleFullstendigOrThrow(),
                        ),
                    ),
                ),
            )
            val simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak =
                simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
                    stønadsperiode = stønadsperiode,
                    revurderingsperiode = revurderingsperiode,
                    grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                        grunnlagsdataEnsligUtenFradrag(periode = revurderingsperiode),
                        vilkårsvurderinger,
                    ),
                ).second
            on { hent(revurderingId) } doReturn simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
        }

        val actual = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        )

        actual shouldBe KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(
            listOf(
                RevurderingsutfallSomIkkeStøttes.OpphørErIkkeFraFørsteMåned,
            ),
        ).left()

        verify(revurderingRepoMock, never()).lagre(any(), anyOrNull())
    }

    @Test
    fun `uføreopphør kan ikke gjøres i kombinasjon med fradragsendringer`() {
        val stønadsperiode = RevurderingTestUtils.stønadsperiodeNesteMånedOgTreMånederFram
        val revurderingsperiode = stønadsperiode.periode
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak(
                stønadsperiode = stønadsperiode,
                revurderingsperiode = revurderingsperiode,
                grunnlagsdataOgVilkårsvurderinger = grunnlagsdataEnsligMedFradrag(periode = revurderingsperiode).let {
                    GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                        it,
                        vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgAndreInnvilget(
                            periode = stønadsperiode.periode,
                            bosituasjon = Nel.fromListUnsafe(it.bosituasjon.map { it as Grunnlag.Bosituasjon.Fullstendig }),
                        ),
                    )
                },
            ).second
        }
        val actual = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
        ).sendTilAttestering(
            SendTilAttesteringRequest(
                revurderingId = revurderingId,
                saksbehandler = saksbehandler,
                fritekstTilBrev = "Fritekst",
                skalFøreTilBrevutsending = true,
            ),
        )

        actual shouldBe KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(
            listOf(
                RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
            ),
        ).left()

        verify(revurderingRepoMock, never()).lagre(any(), anyOrNull())
    }

    @Test
    fun `får ikke sendt til attestering dersom det eksisterer åpne kravgrunnlag for sak`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn listOf(
                    AvventerKravgrunnlag(
                        avgjort = Tilbakekrev(
                            id = UUID.randomUUID(),
                            opprettet = fixedTidspunkt,
                            sakId = sakId,
                            revurderingId = revurderingId,
                            periode = år(2021),
                        ),
                    ),
                )
            },
        ).let {
            it.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = revurderingId,
                    saksbehandler = saksbehandler,
                    fritekstTilBrev = "Fritekst",
                    skalFøreTilBrevutsending = true,
                ),
            ) shouldBe KunneIkkeSendeRevurderingTilAttestering.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving(
                revurderingId,
            ).left()
        }
    }
}
