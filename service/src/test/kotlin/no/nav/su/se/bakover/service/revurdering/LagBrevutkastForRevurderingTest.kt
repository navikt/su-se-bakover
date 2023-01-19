package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument
import no.nav.su.se.bakover.domain.revurdering.SimulertRevurdering
import no.nav.su.se.bakover.domain.revurdering.brev.KunneIkkeLageBrevutkastForRevurdering
import no.nav.su.se.bakover.domain.visitor.LagBrevRequestVisitor
import no.nav.su.se.bakover.domain.visitor.Visitable
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.beregnetRevurdering
import no.nav.su.se.bakover.test.fixedTidspunkt
import no.nav.su.se.bakover.test.opprettetRevurdering
import no.nav.su.se.bakover.test.revurderingId
import no.nav.su.se.bakover.test.simulertRevurdering
import no.nav.su.se.bakover.test.tikkendeFixedClock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

internal class LagBrevutkastForRevurderingTest {

    @Test
    fun `lagBrevutkast - kan lage brev`() {
        val brevPdf = "".toByteArray()

        val simulertRevurdering = simulertRevurdering().second

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
        val revurdering = simulertRevurdering().second as SimulertRevurdering.Innvilget
        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(revurderingId) } doReturn revurdering
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
                verify(it.brevService).lagDokument(argThat<Visitable<LagBrevRequestVisitor>> { it shouldBe revurdering })
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
                verify(it.brevService).lagDokument(argThat<Visitable<LagBrevRequestVisitor>> { it shouldBe revurdering })
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
                verify(it.brevService).lagDokument(argThat<Visitable<LagBrevRequestVisitor>> { it shouldBe revurdering })
                it.verifyNoMoreInteractions()
            }
        }
    }

    @Test
    fun `kan ikke lage brev med status opprettet`() {
        val (_, opprettetRevurdering) = opprettetRevurdering()

        assertThrows<LagBrevRequestVisitor.KanIkkeLageBrevrequestForInstans> {
            RevurderingServiceMocks(
                revurderingRepo = mock {
                    on { hent(any()) } doReturn opprettetRevurdering
                },
                brevService = mock {
                    on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doThrow LagBrevRequestVisitor.KanIkkeLageBrevrequestForInstans(
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
        val clock = tikkendeFixedClock()
        val beregnget = beregnetRevurdering(
            clock = clock,
        ).second

        assertThrows<LagBrevRequestVisitor.KanIkkeLageBrevrequestForInstans> {
            RevurderingServiceMocks(
                revurderingRepo = mock {
                    on { hent(any()) } doReturn beregnget
                },
                brevService = mock {
                    on { lagDokument(any<Visitable<LagBrevRequestVisitor>>()) } doThrow LagBrevRequestVisitor.KanIkkeLageBrevrequestForInstans(
                        beregnget::class,
                    )
                },
            ).revurderingService.lagBrevutkastForRevurdering(
                revurderingId = revurderingId,
                fritekst = "",
            )
        }
    }
}
