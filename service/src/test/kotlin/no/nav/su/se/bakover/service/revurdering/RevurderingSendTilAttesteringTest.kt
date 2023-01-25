package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.juli
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.common.periode.februar
import no.nav.su.se.bakover.common.periode.mai
import no.nav.su.se.bakover.common.periode.mars
import no.nav.su.se.bakover.common.periode.år
import no.nav.su.se.bakover.common.september
import no.nav.su.se.bakover.common.toNonEmptyList
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.singleFullstendigOrThrow
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.AvventerKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrev
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveFeil
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.person.KunneIkkeHentePerson
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeSendeRevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.SendTilAttesteringRequest
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.opphør.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.søknadsbehandling.stønadsperiode.Stønadsperiode
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.service.argThat
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
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.stønadsperiode2021
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class RevurderingSendTilAttesteringTest {

    @Test
    fun `sender til attestering`() {
        val (sak, simulertRevurdering) = simulertRevurdering(
            stønadsperiode = stønadsperiode2021,
            revurderingsperiode = Periode.create(fraOgMed = 1.juli(2021), tilOgMed = 30.september(2021)),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveId").right()
                on { lukkOppgave(any()) } doReturn Unit.right()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            observer = mock(),
        ).also {
            val actual = it.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = simulertRevurdering.id,
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail()

            inOrder(it.revurderingRepo, it.personService, it.oppgaveService, it.observer) {
                verify(it.revurderingRepo).hent(argThat { it shouldBe simulertRevurdering.id })
                verify(it.personService).hentAktørId(argThat { it shouldBe fnr })
                verify(it.oppgaveService).opprettOppgave(
                    argThat {
                        it shouldBe OppgaveConfig.AttesterRevurdering(
                            saksnummer = saksnummer,
                            aktørId = aktørId,
                            tilordnetRessurs = null,
                            clock = fixedClock,
                        )
                    },
                )
                verify(it.oppgaveService).lukkOppgave(argThat { it shouldBe simulertRevurdering.oppgaveId })
                verify(it.revurderingRepo).defaultTransactionContext()
                verify(it.revurderingRepo).lagre(argThat { it shouldBe actual }, anyOrNull())
                verify(it.observer).handle(
                    argThat {
                        it shouldBe StatistikkEvent.Behandling.Revurdering.TilAttestering.Innvilget(
                            actual as RevurderingTilAttestering.Innvilget,
                        )
                    },
                )
            }

            verifyNoMoreInteractions(it.revurderingRepo, it.personService, it.oppgaveService)
        }
    }

    @Test
    fun `sender ikke til attestering hvis revurdering er ikke simulert`() {
        val (sak, opprettetRevurdering) = opprettetRevurdering()

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        ).also {
            val result = it.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = opprettetRevurdering.id,
                    saksbehandler = saksbehandler,
                ),
            )

            result shouldBe KunneIkkeSendeRevurderingTilAttestering.UgyldigTilstand(
                OpprettetRevurdering::class,
                RevurderingTilAttestering::class,
            ).left()

            verify(it.revurderingRepo).hent(opprettetRevurdering.id)
            verifyNoMoreInteractions(it.revurderingRepo)
        }
    }

    @Test
    fun `sender ikke til attestering hvis henting av aktørId feiler`() {
        val (sak, revurdering) = simulertRevurdering(
            stønadsperiode = stønadsperiode2021,
            revurderingsperiode = Periode.create(fraOgMed = 1.juli(2021), tilOgMed = 30.september(2021)),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurdering.id) } doReturn revurdering
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn KunneIkkeHentePerson.FantIkkePerson.left()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        ).let { mocks ->
            val actual = mocks.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = revurdering.id,
                    saksbehandler = saksbehandler,
                ),
            )

            actual shouldBe KunneIkkeSendeRevurderingTilAttestering.FantIkkeAktørId.left()

            inOrder(*mocks.all()) {
                verify(mocks.revurderingRepo).hent(argThat { it shouldBe revurdering.id })
                verify(mocks.sakService).hentSakForRevurdering(revurdering.id)
                verify(mocks.tilbakekrevingService).hentAvventerKravgrunnlag(any<UUID>())
                verify(mocks.personService).hentAktørId(argThat { it shouldBe revurdering.fnr })
                mocks.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `sender ikke til attestering hvis oppretting av oppgave feiler`() {
        val (sak, revurdering) = simulertRevurdering(
            stønadsperiode = stønadsperiode2021,
            revurderingsperiode = Periode.create(fraOgMed = 1.juli(2021), tilOgMed = 30.september(2021)),
        )

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
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        ).let { mocks ->
            val actual = mocks.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = revurderingId,
                    saksbehandler = saksbehandler,
                ),
            )

            actual shouldBe KunneIkkeSendeRevurderingTilAttestering.KunneIkkeOppretteOppgave.left()

            inOrder(
                *mocks.all(),
            ) {
                verify(mocks.revurderingRepo).hent(revurderingId)
                verify(mocks.sakService).hentSakForRevurdering(revurdering.id)
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
    fun `får ikke sende til attestering dersom tilbakekreving ikke er ferdigbehandlet`() {
        val (sak, revurdering) = simulertRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 5000.0,
                ),
            ),
        )

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
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        ).let {
            it.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = revurderingId,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe KunneIkkeSendeRevurderingTilAttestering.FeilInnvilget(SimulertRevurdering.KunneIkkeSendeInnvilgetRevurderingTilAttestering.TilbakekrevingsbehandlingErIkkeFullstendig)
                .left()
        }
    }

    @Test
    fun `får sende til attestering dersom tilbakekreving er ferdigbehandlet`() {
        val (sak, revurdering) = simulertRevurdering(
            grunnlagsdataOverrides = listOf(
                fradragsgrunnlagArbeidsinntekt(
                    periode = år(2021),
                    arbeidsinntekt = 5000.0,
                ),
            ),
        )

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
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        ).let {
            it.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = revurderingId,
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail() shouldBe beOfType<RevurderingTilAttestering.Innvilget>()
        }
    }

    @Test
    fun `formueopphør må være fra første måned`() {
        val revurderingsperiode = februar(2021)..mai(2021)
        val stønadsperiode = Stønadsperiode.create(revurderingsperiode)
        val førsteUførevurderingsperiode = februar(2021)
        val andreUførevurderingsperiode = mars(2021)..mai(2021)

        val (sak, simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak) = simulertRevurdering(
            stønadsperiode = stønadsperiode,
            revurderingsperiode = revurderingsperiode,
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Formue)),
            vilkårOverrides = listOf(
                FormueVilkår.Vurdert.createFromGrunnlag(
                    grunnlag = nonEmptyListOf(
                        formueGrunnlagUtenEps0Innvilget(
                            periode = førsteUførevurderingsperiode,
                            bosituasjon = grunnlagsdataEnsligUtenFradrag(
                                periode = førsteUførevurderingsperiode,
                            ).bosituasjon.map { it as Grunnlag.Bosituasjon.Fullstendig }.toNonEmptyList(),
                        ),
                        formueGrunnlagUtenEpsAvslått(
                            periode = andreUførevurderingsperiode,
                            bosituasjon = grunnlagsdataEnsligUtenFradrag(
                                periode = andreUførevurderingsperiode,
                            ).bosituasjon.singleFullstendigOrThrow(),
                        ),
                    ),
                ),
            ),
        )
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        ).also {
            val actual = it.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = simulertRevurderingOpphørtUføreFraInnvilgetSøknadsbehandlingsVedtak.id,
                    saksbehandler = saksbehandler,
                ),
            )
            actual shouldBe KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(
                listOf(
                    RevurderingsutfallSomIkkeStøttes.OpphørErIkkeFraFørsteMåned,
                ),
            ).left()

            verify(it.revurderingRepo, never()).lagre(any(), anyOrNull())
        }
    }

    @Test
    fun `uføreopphør kan ikke gjøres i kombinasjon med fradragsendringer`() {
        val revurderingsperiode = februar(2021)..mai(2021)
        val stønadsperiode = Stønadsperiode.create(revurderingsperiode)
        val (sak, simulert) = simulertRevurdering(
            vilkårOverrides = listOf(avslåttUførevilkårUtenGrunnlag(periode = revurderingsperiode)),
            grunnlagsdataOverrides = grunnlagsdataEnsligMedFradrag(periode = revurderingsperiode).let { it.fradragsgrunnlag + it.bosituasjon },
            stønadsperiode = stønadsperiode,
            revurderingsperiode = revurderingsperiode,
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulert
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        ).also {
            val actual = it.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = revurderingId,
                    saksbehandler = saksbehandler,
                ),
            )

            actual shouldBe KunneIkkeSendeRevurderingTilAttestering.RevurderingsutfallStøttesIkke(
                listOf(
                    RevurderingsutfallSomIkkeStøttes.OpphørOgAndreEndringerIKombinasjon,
                ),
            ).left()

            verify(it.revurderingRepo, never()).lagre(any(), anyOrNull())
        }
    }

    @Test
    fun `får ikke sendt til attestering dersom det eksisterer åpne kravgrunnlag for sak`() {
        val (sak, simulert) = simulertRevurdering()
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulert
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
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
                ),
            ) shouldBe KunneIkkeSendeRevurderingTilAttestering.SakHarRevurderingerMedÅpentKravgrunnlagForTilbakekreving(
                revurderingId,
            ).left()
        }
    }
}
