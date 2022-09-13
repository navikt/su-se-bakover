package no.nav.su.se.bakover.service.revurdering

import arrow.core.right
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.behandling.Attestering
import no.nav.su.se.bakover.domain.oppgave.OppgaveConfig
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.domain.statistikk.Statistikkhendelse
import no.nav.su.se.bakover.service.argThat
import no.nav.su.se.bakover.test.aktørId
import no.nav.su.se.bakover.test.fixedClock
import no.nav.su.se.bakover.test.fnr
import no.nav.su.se.bakover.test.getOrFail
import no.nav.su.se.bakover.test.revurderingTilAttestering
import no.nav.su.se.bakover.test.saksbehandler
import no.nav.su.se.bakover.test.saksnummer
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
        val (_, tilAttestering) = revurderingTilAttestering()

        val attestering = Attestering.Underkjent(
            attestant = NavIdentBruker.Attestant(navIdent = "123"),
            grunn = Attestering.Underkjent.Grunn.BEREGNINGEN_ER_FEIL,
            kommentar = "pls math",
            opprettet = Tidspunkt.EPOCH,
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
        ).also {
            val actual = it.revurderingService.underkjenn(
                revurderingId = tilAttestering.id,
                attestering = attestering,
            ).getOrFail()

            actual shouldBe tilAttestering.underkjenn(
                attestering, nyOppgaveId,
            )

            inOrder(it.revurderingRepo, it.personService, it.oppgaveService, it.observer) {
                verify(it.revurderingRepo).hent(argThat { it shouldBe tilAttestering.id })
                verify(it.personService).hentAktørId(argThat { it shouldBe fnr })
                verify(it.oppgaveService).opprettOppgave(
                    argThat {
                        it shouldBe OppgaveConfig.Revurderingsbehandling(
                            saksnummer = saksnummer,
                            aktørId = aktørId,
                            tilordnetRessurs = saksbehandler,
                            clock = fixedClock,
                        )
                    },
                )
                verify(it.revurderingRepo).defaultTransactionContext()
                verify(it.revurderingRepo).lagre(argThat { it shouldBe actual }, anyOrNull())
                verify(it.oppgaveService).lukkOppgave(argThat { it shouldBe tilAttestering.oppgaveId })

                verify(it.observer).handle(
                    argThat {
                        it shouldBe Statistikkhendelse.Revurdering.Underkjent(actual)
                    },
                )
            }
            verifyNoMoreInteractions(it.revurderingRepo, it.personService, it.oppgaveService)
        }
    }
}
