package no.nav.su.se.bakover.service.revurdering

import arrow.core.right
import behandling.domain.UnderkjennAttesteringsgrunnBehandling
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.attestering.Attestering
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppgave.OppdaterOppgaveInfo
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.oppgave.domain.Oppgavetype
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.oppgave.nyOppgaveHttpKallResponse
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.saksbehandler
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoMoreInteractions

internal class UnderkjennRevurderingTest {

    @Test
    fun `underkjenn - underkjenner en revurdering`() {
        val clock = TikkendeKlokke()
        val (_, tilAttestering) = revurderingTilAttestering(
            clock = clock,
        )

        val attestering = Attestering.Underkjent(
            attestant = NavIdentBruker.Attestant(navIdent = "123"),
            grunn = UnderkjennAttesteringsgrunnBehandling.BEREGNINGEN_ER_FEIL,
            kommentar = "pls math",
            opprettet = Tidspunkt.now(clock),
        )

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn tilAttestering
            },
            oppgaveService = mock {
                on { oppdaterOppgave(any(), any()) } doReturn nyOppgaveHttpKallResponse().right()
            },
            observer = mock(),
        ).also { mocks ->
            val actual = mocks.revurderingService.underkjenn(
                revurderingId = tilAttestering.id,
                attestering = attestering,
            ).getOrFail()

            actual shouldBe tilAttestering.underkjenn(attestering)

            inOrder(mocks.revurderingRepo, mocks.personService, mocks.oppgaveService, mocks.observer) {
                verify(mocks.revurderingRepo).hent(argThat { it shouldBe tilAttestering.id })
                verify(mocks.revurderingRepo).defaultTransactionContext()
                verify(mocks.revurderingRepo).lagre(argThat { it shouldBe actual }, anyOrNull())
                verify(mocks.oppgaveService).oppdaterOppgave(
                    argThat { it shouldBe OppgaveId("oppgaveIdRevurdering") },
                    argThat {
                        it shouldBe OppdaterOppgaveInfo(
                            "Revurderingen er blitt underkjent",
                            oppgavetype = Oppgavetype.BEHANDLE_SAK,
                            tilordnetRessurs = OppdaterOppgaveInfo.TilordnetRessurs.NavIdent(saksbehandler.navIdent),
                        )
                    },
                )

                verify(mocks.observer).handle(
                    argThat {
                        it shouldBe StatistikkEvent.Behandling.Revurdering.Underkjent.Innvilget(actual as UnderkjentRevurdering.Innvilget)
                    },
                )
            }
            verifyNoMoreInteractions(mocks.revurderingRepo, mocks.personService, mocks.oppgaveService)
        }
    }
}
