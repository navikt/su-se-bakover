package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.su.se.bakover.common.august
import no.nav.su.se.bakover.common.desember
import no.nav.su.se.bakover.common.mai
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import no.nav.su.se.bakover.domain.grunnlag.Uføregrad
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.KunneIkkeSendeRevurderingTilAttestering
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.SendTilAttesteringRequest
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.vilkår.UføreVilkår
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevilkårRequest
import no.nav.su.se.bakover.domain.vilkår.uføre.LeggTilUførevurderingerRequest
import no.nav.su.se.bakover.domain.vilkår.uføre.UførevilkårStatus
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.revurdering.RevurderingTestUtils.revurderingsårsakRegulerGrunnbeløp
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.revurderingUnderkjent
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.tikkendeFixedClock
import no.nav.su.se.bakover.test.vilkår.flyktningVilkårAvslått
import no.nav.su.se.bakover.test.vilkårsvurderinger.avslåttUførevilkårUtenGrunnlag
import no.nav.su.se.bakover.test.vilkårsvurderinger.innvilgetUførevilkår
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class RegulerGrunnbeløpServiceImplTest {

    @Test
    fun `oppdaterer uførevilkåret når nytt uføregrunnlag legges til`() {
        val (sak, opprettetRevurdering) = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Uførhet)),
        )

        val nyttUføregrunnlag = Grunnlag.Uføregrunnlag(
            id = UUID.randomUUID(),
            opprettet = fixedTidspunkt,
            periode = opprettetRevurdering.periode,
            uføregrad = Uføregrad.parse(45),
            forventetInntekt = 20,
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn opprettetRevurdering
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).also {
            it.revurderingService.leggTilUførevilkår(
                LeggTilUførevurderingerRequest(
                    behandlingId = revurderingId,
                    vurderinger = nonEmptyListOf(
                        LeggTilUførevilkårRequest(
                            behandlingId = nyttUføregrunnlag.id,
                            periode = nyttUføregrunnlag.periode,
                            uføregrad = nyttUføregrunnlag.uføregrad,
                            forventetInntekt = nyttUføregrunnlag.forventetInntekt,
                            oppfylt = UførevilkårStatus.VilkårOppfylt,
                            begrunnelse = "grunnbeløpet er høyere",
                        ),
                    ),
                ),
            )

            verify(it.revurderingRepo).hent(argThat { it shouldBe revurderingId })
            verify(it.revurderingRepo).defaultTransactionContext()
            verify(it.revurderingRepo).lagre(
                argThat {
                    it shouldBe opprettetRevurdering.copy(
                        vilkårsvurderinger = opprettetRevurdering.vilkårsvurderinger.leggTil(
                            innvilgetUførevilkår(
                                vurderingsperiodeId = (
                                    it.vilkårsvurderinger.uføreVilkår()
                                        .getOrFail() as UføreVilkår.Vurdert
                                    ).vurderingsperioder.first().id,
                                grunnlagsId = (
                                    it.vilkårsvurderinger.uføreVilkår()
                                        .getOrFail() as UføreVilkår.Vurdert
                                    ).grunnlag.first().id,
                                opprettet = fixedTidspunkt,
                                periode = nyttUføregrunnlag.periode,
                                forventetInntekt = nyttUføregrunnlag.forventetInntekt,
                                uføregrad = nyttUføregrunnlag.uføregrad,
                            ),
                        ),
                        informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Uførhet))
                            .markerSomVurdert(Revurderingsteg.Uførhet),
                    )
                },
                anyOrNull(),
            )
        }
    }

    @Test
    fun `G-regulering med uendret fradrag og forventetInntekt fører til IngenEndring`() {
        val (sak, revurdering) = opprettetRevurdering(
            revurderingsperiode = Periode.create(1.mai(2021), 31.desember(2021)),
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn revurdering
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
        ).also {
            val actual = it.revurderingService.beregnOgSimuler(
                revurderingId = revurdering.id,
                saksbehandler = saksbehandler,
            ).getOrFail().revurdering

            inOrder(
                *it.all(),
            ) {
                verify(it.sakService).hentSakForRevurdering(revurdering.id)
                verify(it.revurderingRepo).defaultTransactionContext()
                verify(it.revurderingRepo).lagre(argThat { it shouldBe actual }, anyOrNull())
                verify(it.sakService).hentSakForRevurdering(revurdering.id)
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `Ikke lov å sende en Simulert Opphørt til attestering`() {
        val (sak, simulertRevurdering) = simulertRevurdering(
            revurderingsperiode = Periode.create(1.august(2021), 31.desember(2021)),
            revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
            vilkårOverrides = listOf(
                avslåttUførevilkårUtenGrunnlag(
                    periode = Periode.create(1.august(2021), 31.desember(2021)),
                ),
            ),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn simulertRevurdering
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn OppgaveId("oppgaveid").right()
                on { lukkOppgave(any()) } doReturn Unit.right()
            },
            sakService = mock {
                on { hentSakForRevurdering(any()) } doReturn sak
            },
            tilbakekrevingService = mock {
                on { hentAvventerKravgrunnlag(any<UUID>()) } doReturn emptyList()
            },
        ).also {
            it.revurderingService.sendTilAttestering(
                SendTilAttesteringRequest(
                    revurderingId = simulertRevurdering.id,
                    saksbehandler = saksbehandler,
                ),
            ) shouldBe KunneIkkeSendeRevurderingTilAttestering.FeilOpphørt(SimulertRevurdering.Opphørt.KanIkkeSendeOpphørtRevurderingTilAttestering.KanIkkeSendeEnOpphørtGReguleringTilAttestering).left()

            inOrder(it.revurderingRepo, it.personService, it.oppgaveService) {
                verify(it.revurderingRepo).hent(simulertRevurdering.id)
                verify(it.personService).hentAktørId(argThat { it shouldBe fnr })
                verify(it.oppgaveService).opprettOppgave(any())
                verify(it.oppgaveService).lukkOppgave(any())
            }
            verifyNoMoreInteractions(it.revurderingRepo, it.personService, it.oppgaveService)
        }
    }

    @Test
    fun `Ikke lov å sende en Underkjent Opphørt til attestering`() {
        assertThrows<AssertionError> {
            revurderingUnderkjent(
                revurderingsårsak = revurderingsårsakRegulerGrunnbeløp,
                clock = tikkendeFixedClock,
                vilkårOverrides = listOf(
                    flyktningVilkårAvslått(),
                ),
            )
        }.also {
            it.message shouldContain "KanIkkeSendeEnOpphørtGReguleringTilAttestering"
        }
    }
}
