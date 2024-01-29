package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import dokument.domain.Dokument
import dokument.domain.KunneIkkeLageDokument
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import no.nav.su.se.bakover.domain.brev.command.IverksettRevurderingDokumentCommand
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.pdfATom
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.tikkendeFixedClock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class LagBrevutkastForVilkårsvurderingerRevurderingTest {

    @Test
    fun `lagBrevutkast - kan lage brev`() {
        val simulertRevurdering = simulertRevurdering().second

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn simulertRevurdering
            },
            brevService = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn Dokument.UtenMetadata.Vedtak(
                    opprettet = fixedTidspunkt,
                    tittel = "tittel1",
                    generertDokument = pdfATom(),
                    generertDokumentJson = "brev",
                ).right()
            },
        ).let {
            it.revurderingService.lagBrevutkastForRevurdering(
                revurderingId = revurderingId,
            ) shouldBe pdfATom().right()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(argThat { it shouldBe revurderingId })
                verify(it.brevService).lagDokument(argThat { it shouldBe beOfType<IverksettRevurderingDokumentCommand.Inntekt>() }, anyOrNull())
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `lagBrevutkast - får feil når vi ikke kan hente person`() {
        val revurdering = simulertRevurdering().second as SimulertRevurdering.Innvilget
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn revurdering
            },
            brevService = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn KunneIkkeLageDokument.FeilVedHentingAvInformasjon.left()
            },
        ).let {
            it.revurderingService.lagBrevutkastForRevurdering(
                revurderingId = revurderingId,
            ) shouldBe KunneIkkeLageBrevutkastForRevurdering.KunneIkkeGenererePdf(
                KunneIkkeLageDokument.FeilVedHentingAvInformasjon,
            ).left()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(argThat { it shouldBe revurderingId })
                verify(it.brevService).lagDokument(any(), anyOrNull())
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `får feil når vi ikke kan hente saksbehandler navn`() {
        val revurdering = simulertRevurdering().second as SimulertRevurdering.Innvilget
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn revurdering
            },
            brevService = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn KunneIkkeLageDokument.FeilVedHentingAvInformasjon.left()
            },
        ).let {
            it.revurderingService.lagBrevutkastForRevurdering(
                revurderingId = revurderingId,
            ) shouldBe KunneIkkeLageBrevutkastForRevurdering.KunneIkkeGenererePdf(
                KunneIkkeLageDokument.FeilVedHentingAvInformasjon,
            ).left()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(argThat { it shouldBe revurderingId })
                verify(it.brevService).lagDokument(any(), anyOrNull())
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `får feil når vi ikke kan lage brev`() {
        val revurdering = simulertRevurdering().second as SimulertRevurdering.Innvilget
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn revurdering
            },
            brevService = mock {
                on { lagDokument(any(), anyOrNull()) } doReturn KunneIkkeLageDokument.FeilVedGenereringAvPdf.left()
            },
        ).let {
            it.revurderingService.lagBrevutkastForRevurdering(
                revurderingId = revurderingId,
            ) shouldBe KunneIkkeLageBrevutkastForRevurdering.KunneIkkeGenererePdf(
                KunneIkkeLageDokument.FeilVedGenereringAvPdf,
            ).left()

            inOrder(
                *it.all(),
            ) {
                verify(it.revurderingRepo).hent(argThat { it shouldBe revurderingId })
                verify(it.brevService).lagDokument(any(), anyOrNull())
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `kan ikke lage brev med status opprettet`() {
        val (_, opprettetRevurdering) = opprettetRevurdering()

        assertThrows<IllegalArgumentException> {
            RevurderingServiceMocks(
                revurderingRepo = mock {
                    on { hent(any()) } doReturn opprettetRevurdering
                },
                brevService = mock {
                    on { lagDokument(any(), anyOrNull()) } doThrow IllegalArgumentException("fra en test")
                },
            ).let {
                it.revurderingService.lagBrevutkastForRevurdering(
                    revurderingId = revurderingId,
                )

                verify(it.revurderingRepo).hent(argThat { it shouldBe opprettetRevurdering.id })
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `kan ikke lage brev med status beregnet`() {
        val clock = tikkendeFixedClock()
        val beregnget = beregnetRevurdering(
            clock = clock,
        ).second

        assertThrows<IllegalArgumentException> {
            RevurderingServiceMocks(
                revurderingRepo = mock {
                    on { hent(any()) } doReturn beregnget
                },
                brevService = mock {
                    on { lagDokument(any(), anyOrNull()) } doThrow IllegalArgumentException("fra en test")
                },
            ).revurderingService.lagBrevutkastForRevurdering(
                revurderingId = revurderingId,
            )
        }
    }
}
