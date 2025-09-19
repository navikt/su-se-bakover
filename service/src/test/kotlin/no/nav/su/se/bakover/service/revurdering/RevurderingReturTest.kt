package no.nav.su.se.bakover.service.revurdering

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.revurdering.IverksattRevurdering
import no.nav.su.se.bakover.domain.revurdering.RevurderingId
import no.nav.su.se.bakover.domain.revurdering.repo.RevurderingRepo
import no.nav.su.se.bakover.domain.revurdering.retur.KunneIkkeReturnereRevurdering
import no.nav.su.se.bakover.domain.revurdering.service.RevurderingService
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.revurderingTilAttestering
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.UUID

internal class RevurderingReturTest {

    @Test
    fun `returner revurdering, feiler hvis den ikke finnes`() {
        val service = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
                on { hent(any()) } doReturn null
            },
        )

        val svar = service.revurderingService.returnerRevurdering(
            RevurderingService.ReturnerRevurderingRequest
                (RevurderingId(UUID.randomUUID()), NavIdentBruker.Saksbehandler("Ident")),
        )
        svar shouldBe KunneIkkeReturnereRevurdering.FantIkkeRevurdering.left()
    }

    @Test
    fun `returner revurdering, feiler hvis revurderingen er i ugyldig tilstand for retur`() {
        val service = RevurderingServiceMocks(
            revurderingRepo = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
                on { hent(any()) } doReturn mock<IverksattRevurdering.Innvilget>()
            },
        )
        val svar = service.revurderingService.returnerRevurdering(
            RevurderingService.ReturnerRevurderingRequest
                (RevurderingId(UUID.randomUUID()), NavIdentBruker.Saksbehandler("Ident")),
        )
        svar shouldBe KunneIkkeReturnereRevurdering.FeilSaksbehandler.left()
    }

    @Test
    fun `returner revurdering, feiler hvis det er feil saksbehandler`() {
        val(_, revurderingsmock) = revurderingTilAttestering(saksbehandler = NavIdentBruker.Saksbehandler("Annen Saksbehandler"))

        val service = RevurderingServiceMocks(
            revurderingRepo = mock<RevurderingRepo> {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
                on { hent(any()) } doReturn revurderingsmock
            },
        )
        val svar = service.revurderingService.returnerRevurdering(
            RevurderingService.ReturnerRevurderingRequest
                (RevurderingId(UUID.randomUUID()), NavIdentBruker.Saksbehandler("Feil saksbehandler")),
        )
        svar shouldBe KunneIkkeReturnereRevurdering.FeilSaksbehandler.left()
    }

    @Test
    fun `happy case`() {
        val revurderingId = RevurderingId(UUID.randomUUID())
        val riktigSaksbehandler = NavIdentBruker.Saksbehandler("Ident")
        val (_, revurderingsmock) = revurderingTilAttestering(saksbehandler = riktigSaksbehandler)

        val revurderingRepo = mock<RevurderingRepo> {
            on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            on { lagre(any(), any()) } doAnswer { mock() }
            on { hent(any()) } doReturn revurderingsmock
        }
        val service = RevurderingServiceMocks(
            revurderingRepo = revurderingRepo,
            oppgaveService = mock {
                on { oppdaterOppgave(any(), any()) } doAnswer { mock(OppgaveHttpKallResponse::class.java).right() }
            },
        )
        val svar = service.revurderingService.returnerRevurdering(
            RevurderingService.ReturnerRevurderingRequest
                (RevurderingId(revurderingId.value), riktigSaksbehandler),
        )
        svar.shouldBeRight()

        verify(revurderingRepo, times(1)).hent(revurderingId)
    }
}
