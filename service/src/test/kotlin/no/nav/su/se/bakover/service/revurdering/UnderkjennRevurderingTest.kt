package no.nav.su.se.bakover.service.revurdering

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.domain.Attestering
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.revurdering.UnderkjentRevurdering
import no.nav.su.se.bakover.domain.statistikk.StatistikkEvent
import no.nav.su.se.bakover.test.TikkendeKlokke
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.argThat
import no.nav.su.se.bakover.test.getOrFail
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
        val (sak, tilAttestering) = revurderingTilAttestering(
            clock = clock,
        )

        val attestering = Attestering.Underkjent(
            attestant = NavIdentBruker.Attestant(navIdent = "123"),
            grunn = Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL,
            kommentar = "pls math",
            opprettet = Tidspunkt.now(clock),
        )

        val nyOppgaveId = OppgaveId("nyOppgaveId")

        RevurderingServiceMocks(
            revurderingRepo = mock {
                on { hent(any()) } doReturn tilAttestering
            },
            personService = mock {
                on { hentAktørId(any()) } doReturn aktørId.right()
            },
            oppgaveService = mock {
                on { opprettOppgave(any()) } doReturn nyOppgaveId.right()
                on { lukkOppgave(any()) } doReturn Unit.right()
            },
            observer = mock(),
        ).also { mocks ->
            val actual = mocks.revurderingService.underkjenn(
                revurderingId = tilAttestering.id,
                attestering = attestering,
            ).getOrFail()

            actual shouldBe tilAttestering.underkjenn(
                attestering,
                nyOppgaveId,
            )

            inOrder(mocks.revurderingRepo, mocks.personService, mocks.oppgaveService, mocks.observer) {
                verify(mocks.revurderingRepo).hent(argThat { it shouldBe tilAttestering.id })
                verify(mocks.personService).hentAktørId(argThat { it shouldBe sak.fnr })
                verify(mocks.oppgaveService).opprettOppgave(
                    argThat {
                        it shouldBe OppgaveConfig.Revurderingsbehandling(
                            saksnummer = sak.saksnummer,
                            aktørId = aktørId,
                            tilordnetRessurs = saksbehandler,
                            clock = mocks.clock,
                        )
                    },
                )
                verify(mocks.revurderingRepo).defaultTransactionContext()
                verify(mocks.revurderingRepo).lagre(argThat { it shouldBe actual }, anyOrNull())
                verify(mocks.oppgaveService).lukkOppgave(argThat { it shouldBe tilAttestering.oppgaveId })

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
