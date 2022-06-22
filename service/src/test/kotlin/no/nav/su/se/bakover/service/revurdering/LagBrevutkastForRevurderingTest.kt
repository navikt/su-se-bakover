package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.nonEmptyListOf
import arrow.core.right
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.grunnlag.Grunnlagsdata
import no.nav.su.se.bakover.domain.grunnlag.GrunnlagsdataOgVilkårsvurderinger
import no.nav.su.se.bakover.domain.oppdrag.SimulerUtbetalingRequest
import no.nav.su.se.bakover.domain.oppdrag.Utbetaling
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.revurdering.InformasjonSomRevurderes
import no.nav.su.se.bakover.domain.revurdering.OpprettetRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.Revurderingsteg
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
import no.nav.su.se.bakover.service.utbetaling.UtbetalingService
import no.nav.su.se.bakover.test.beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.grunnlagsdataEnsligMedFradrag
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.sakId
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak
import no.nav.su.se.bakover.test.utbetalingslinje
import no.nav.su.se.bakover.test.vedtakSøknadsbehandlingIverksattInnvilget
import no.nav.su.se.bakover.test.vilkår.formuevilkårIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurderingRevurderingIkkeVurdert
import no.nav.su.se.bakover.test.vilkårsvurderingerAvslåttUføreOgAndreInnvilget
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import java.util.UUID

internal class LagBrevutkastForRevurderingTest {

    @Test
    fun `lagBrevutkast - kan lage brev`() {
        val brevPdf = "".toByteArray()

        val simulertRevurdering = simulertRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn simulertRevurdering
            },
            brevService = mock {
                on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn Dokument.UtenMetadata.Vedtak(
                    opprettet = fixedTidspunkt,
                    tittel = "tittel1",
                    generertDokument = brevPdf,
                    generertDokumentJson = "brev",
                ).right()
            },
        ).let {
            it.revurderingService.lagBrevutkastForRevurdering(
                revurderingId = revurderingId,
                fritekst = "",
            ) shouldBe brevPdf.right()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(argThat { it shouldBe revurderingId })
                verify(it.brevService).lagDokument(argThat<Visitable<LagBrevRequestVisitor>> { it shouldBe simulertRevurdering })
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `lagBrevutkast - får feil når vi ikke kan hente person`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn RevurderingTestUtils.simulertRevurderingInnvilget
            },
            brevService = mock {
                on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn KunneIkkeLageDokument.KunneIkkeHentePerson.left()
            },
        ).let {
            it.revurderingService.lagBrevutkastForRevurdering(
                revurderingId = revurderingId,
                fritekst = "",
            ) shouldBe KunneIkkeLageBrevutkastForRevurdering.FantIkkePerson.left()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(argThat { it shouldBe revurderingId })
                verify(it.brevService).lagDokument(argThat<Visitable<LagBrevRequestVisitor>> { it shouldBe RevurderingTestUtils.simulertRevurderingInnvilget })
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `får feil når vi ikke kan hente saksbehandler navn`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn RevurderingTestUtils.simulertRevurderingInnvilget
            },
            brevService = mock {
                on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn KunneIkkeLageDokument.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left()
            },
        ).let {
            it.revurderingService.lagBrevutkastForRevurdering(
                revurderingId = revurderingId,
                fritekst = "",
            ) shouldBe KunneIkkeLageBrevutkastForRevurdering.KunneIkkeHenteNavnForSaksbehandlerEllerAttestant.left()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(argThat { it shouldBe revurderingId })
                verify(it.brevService).lagDokument(argThat<Visitable<LagBrevRequestVisitor>> { it shouldBe RevurderingTestUtils.simulertRevurderingInnvilget })
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `får feil når vi ikke kan lage brev`() {
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn RevurderingTestUtils.simulertRevurderingInnvilget
            },
            brevService = mock {
                on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doReturn KunneIkkeLageDokument.KunneIkkeGenererePDF.left()
            },
        ).let {
            it.revurderingService.lagBrevutkastForRevurdering(
                revurderingId = revurderingId,
                fritekst = "",
            ) shouldBe KunneIkkeLageBrevutkastForRevurdering.KunneIkkeLageBrevutkast.left()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(argThat { it shouldBe revurderingId })
                verify(it.brevService).lagDokument(argThat<Visitable<LagBrevRequestVisitor>> { it shouldBe RevurderingTestUtils.simulertRevurderingInnvilget })
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `kan ikke lage brev med status opprettet`() {
        val opprettetRevurdering = OpprettetRevurdering(
            id = UUID.randomUUID(),
            periode = RevurderingTestUtils.periodeNesteMånedOgTreMånederFram,
            opprettet = fixedTidspunkt,
            tilRevurdering = vedtakSøknadsbehandlingIverksattInnvilget().second,
            saksbehandler = saksbehandler,
            oppgaveId = OppgaveId("oppgaveid"),
            fritekstTilBrev = "",
            revurderingsårsak = RevurderingTestUtils.revurderingsårsak,
            forhåndsvarsel = null,
            grunnlagsdata = Grunnlagsdata.IkkeVurdert,
            vilkårsvurderinger = vilkårsvurderingRevurderingIkkeVurdert(),
            informasjonSomRevurderes = InformasjonSomRevurderes.create(listOf(Revurderingsteg.Inntekt)),
            avkorting = AvkortingVedRevurdering.Uhåndtert.IngenUtestående,
        )

        assertThrows<LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans> {
            RevurderingServiceMocks(
                revurderingRepo = mock {
                    on { hent(any()) } doReturn opprettetRevurdering
                },
                brevService = mock {
                    on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doThrow LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(
                        opprettetRevurdering::class,
                    )
                },
            ).let {
                it.revurderingService.lagBrevutkastForRevurdering(
                    revurderingId = revurderingId,
                    fritekst = "",
                )

                verify(it.revurderingRepo).hent(argThat { it shouldBe opprettetRevurdering.id })
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `kan ikke lage brev med status beregnet`() {
        val beregnget = beregnetRevurderingInnvilgetFraInnvilgetSøknadsbehandlingsVedtak().second

        assertThrows<LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans> {
            RevurderingServiceMocks(
                revurderingRepo = mock {
                    on { hent(any()) } doReturn beregnget
                },
                brevService = mock {
                    on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doThrow LagBrevRequestVisitor.KunneIkkeLageBrevRequest.KanIkkeLageBrevrequestForInstans(
                        beregnget::class,
                    )
                },
            ).revurderingService.lagBrevutkastForRevurdering(
                revurderingId = revurderingId,
                fritekst = "",
            )
        }
    }

    @Test
    fun `hvis vilkår ikke er oppfylt, fører revurderingen til et opphør`() {
        val simulertUtbetalingMock = mock<Utbetaling.SimulertUtbetaling> {
            on { simulering } doReturn mock()
        }

        val (sak, revurdering) = opprettetRevurderingFraInnvilgetSøknadsbehandlingsVedtak(
            grunnlagsdataOgVilkårsvurderinger = GrunnlagsdataOgVilkårsvurderinger.Revurdering(
                grunnlagsdata = grunnlagsdataEnsligMedFradrag(),
                vilkårsvurderinger = vilkårsvurderingerAvslåttUføreOgAndreInnvilget(),
            ),
        )
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn revurdering
        }
        val utbetalingMock = mock<Utbetaling.OversendtUtbetaling.MedKvittering> {
            on { utbetalingslinjer } doReturn nonEmptyListOf(
                utbetalingslinje(
                    periode = RevurderingTestUtils.periodeNesteMånedOgTreMånederFram,
                    beløp = 20000,
                ),
            )
        }
        val utbetalingServiceMock = mock<UtbetalingService> {
            on { simulerOpphør(any()) } doReturn simulertUtbetalingMock.right()
            on { hentUtbetalingerForSakId(any()) } doReturn listOf(utbetalingMock)
        }

        val actual = RevurderingTestUtils.createRevurderingService(
            revurderingRepo = revurderingRepoMock,
            utbetalingService = utbetalingServiceMock,
            vedtakService = mock {
                on {
                    kopierGjeldendeVedtaksdata(
                        any(),
                        any(),
                    )
                } doReturn sak.kopierGjeldendeVedtaksdata(
                    fraOgMed = revurdering.periode.fraOgMed,
                    clock = fixedClock,
                ).getOrFail().right()
            },
        ).beregnOgSimuler(
            revurderingId = revurderingId,
            saksbehandler = NavIdentBruker.Saksbehandler("s1"),
        ).orNull()!!.revurdering

        actual shouldBe beOfType<SimulertRevurdering.Opphørt>()

        inOrder(
            revurderingRepoMock,
            utbetalingServiceMock,
        ) {
            verify(revurderingRepoMock).hent(revurderingId)
            verify(utbetalingServiceMock).hentUtbetalingerForSakId(sakId)
            verify(utbetalingServiceMock).simulerOpphør(
                argThat {
                    it shouldBe SimulerUtbetalingRequest.Opphør(
                        sakId = sakId,
                        saksbehandler = NavIdentBruker.Saksbehandler("s1"),
                        opphørsdato = revurdering.periode.fraOgMed,
                    )
                },
            )
            verify(revurderingRepoMock).defaultTransactionContext()
            verify(revurderingRepoMock).lagre(argThat { it shouldBe actual }, anyOrNull())
        }
        verifyNoMoreInteractions(revurderingRepoMock, utbetalingServiceMock)
    }

    @Test
    fun `uavklarte vilkår kaster exception`() {
        val (sak, revurdering) = opprettetRevurdering(
            vilkårOverrides = listOf(
                formuevilkårIkkeVurdert()
            )
        )
        val revurderingRepoMock = mock<RevurderingRepo> {
            on { hent(revurderingId) } doReturn revurdering
        }

        val utbetalingServiceMock = mock<UtbetalingService> {
            on { hentUtbetalingerForSakId(any()) } doReturn sak.utbetalinger
        }

        assertThrows<IllegalStateException> {
            RevurderingTestUtils.createRevurderingService(
                revurderingRepo = revurderingRepoMock,
                utbetalingService = utbetalingServiceMock,
                vedtakService = mock {
                    on {
                        kopierGjeldendeVedtaksdata(
                            any(),
                            any(),
                        )
                    } doReturn sak.kopierGjeldendeVedtaksdata(
                        fraOgMed = revurdering.periode.fraOgMed,
                        clock = fixedClock,
                    ).getOrFail()
                        .right()
                },
            ).beregnOgSimuler(
                revurderingId = revurderingId,
                saksbehandler = NavIdentBruker.Saksbehandler("s1"),
            )
        }
    }
}
