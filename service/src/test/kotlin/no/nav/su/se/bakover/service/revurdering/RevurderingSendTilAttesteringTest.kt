package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.common.domain.Stønadsperiode
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.extensions.juli
import no.nav.su.se.bakover.common.extensions.september
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.tid.periode.Periode
import no.nav.su.se.bakover.common.tid.periode.februar
import no.nav.su.se.bakover.common.tid.periode.mai
import no.nav.su.se.bakover.common.tid.periode.mars
import no.nav.su.se.bakover.common.tid.periode.år
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.AvventerKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.Tilbakekrev
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.attestering.KunneIkkeSendeRevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.attestering.SendTilAttesteringRequest
import no.nav.su.se.bakover.domain.revurdering.opphør.RevurderingsutfallSomIkkeStøttes
import no.nav.su.se.bakover.domain.revurdering.steg.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.steg.Revurderingsteg
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.domain.vilkår.FormueVilkår
import no.nav.su.se.bakover.oppgave.domain.KunneIkkeOppdatereOppgave
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.createFromGrunnlag
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fradragsgrunnlagArbeidsinntekt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlag.formueGrunnlagUtenEps0Innvilget
import no.nav.su.se.bakover.test.grunnlag.formueGrunnlagUtenEpsAvslått
import no.nav.su.se.bakover.test.grunnlagsdataEnsligMedFradrag
import no.nav.su.se.bakover.test.grunnlagsdataEnsligUtenFradrag
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
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
import vilkår.domain.grunnlag.Bosituasjon
import vilkår.domain.grunnlag.singleFullstendigOrThrow
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
            oppgaveService = mock {
                on { oppdaterOppgave(any(), any()) } doReturn nyOppgaveHttpKallResponse().right()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
            observer = mock(),
        ).also { mocks ->
            val actual = mocks.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = simulertRevurdering.id,
                    saksbehandler = saksbehandler,
                ),
            ).getOrFail()

            inOrder(mocks.revurderingRepo, mocks.personService, mocks.oppgaveService, mocks.observer) {
                verify(mocks.revurderingRepo).hent(argThat { it shouldBe simulertRevurdering.id })
                verify(mocks.oppgaveService).oppdaterOppgave(
                    argThat { it shouldBe OppgaveId("oppgaveIdRevurdering") },
                    argThat {
                        it shouldBe OppdaterOppgaveInfo(
                            beskrivelse = "Sendt revurdering til attestering",
                            oppgavetype = Oppgavetype.ATTESTERING,
                            tilordnetRessurs = null,
                        )
                    },
                )
                verify(mocks.revurderingRepo).defaultTransactionContext()
                verify(mocks.revurderingRepo).lagre(argThat { it shouldBe actual }, anyOrNull())

                verify(mocks.observer).handle(
                    argThat {
                        it shouldBe StatistikkEvent.Behandling.Revurdering.TilAttestering.Innvilget(
                            actual as RevurderingTilAttestering.Innvilget,
                        )
                    },
                )
            }

            verifyNoMoreInteractions(mocks.revurderingRepo, mocks.personService, mocks.oppgaveService)
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
    fun `sender til attestering selv om oppdatering av oppgave feiler`() {
        val (sak, revurdering) = simulertRevurdering(
            stønadsperiode = stønadsperiode2021,
            revurderingsperiode = Periode.create(fraOgMed = 1.juli(2021), tilOgMed = 30.september(2021)),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn revurdering
            },
            oppgaveService = mock {
                on { oppdaterOppgave(any(), any()) } doReturn KunneIkkeOppdatereOppgave.FeilVedHentingAvOppgave.left()
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
            ).getOrFail()

            inOrder(
                *mocks.all(),
            ) {
                verify(mocks.revurderingRepo).hent(revurderingId)
                verify(mocks.sakService).hentSakForRevurdering(revurdering.id)
                verify(mocks.tilbakekrevingService).hentAvventerKravgrunnlag(any<UUID>())
                verify(mocks.oppgaveService).oppdaterOppgave(
                    argThat { it shouldBe OppgaveId("oppgaveIdRevurdering") },
                    argThat {
                        it shouldBe OppdaterOppgaveInfo(
                            beskrivelse = "Sendt revurdering til attestering",
                            oppgavetype = Oppgavetype.ATTESTERING,
                            tilordnetRessurs = null,
                        )
                    },
                )
                verify(mocks.revurderingRepo).defaultTransactionContext()
                verify(mocks.revurderingRepo).lagre(argThat { it shouldBe actual }, anyOrNull())
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
            // TODO må endre rekkefølge slik at disse ikke kalles
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            // TODO må endre rekkefølge slik at disse ikke kalles
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn nyOppgaveHttpKallResponse().right()
                on { lukkOppgave(any()) } doReturn nyOppgaveHttpKallResponse().right()
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
            oppgaveService = mock {
                on { oppdaterOppgave(any(), any()) } doReturn nyOppgaveHttpKallResponse().right()
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
                            ).bosituasjon.map { it as Bosituasjon.Fullstendig }.toNonEmptyList(),
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
