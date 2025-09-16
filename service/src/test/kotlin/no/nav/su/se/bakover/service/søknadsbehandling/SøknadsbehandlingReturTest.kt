package no.nav.su.se.bakover.service.søknadsbehandling

import arrow.core.left
import arrow.core.right
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.matchers.shouldBe
import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.domain.søknadsbehandling.BeregnetSøknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingId
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingRepo
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService
import no.nav.su.se.bakover.domain.søknadsbehandling.retur.KunneIkkeReturnereSøknadsbehandling
import no.nav.su.se.bakover.oppgave.domain.OppgaveHttpKallResponse
import no.nav.su.se.bakover.test.TestSessionFactory
import no.nav.su.se.bakover.test.søknadsbehandlingTilAttesteringAvslagMedBeregning
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.UUID

internal class SøknadsbehandlingReturTest {

    @Test
    fun `returner behandling, feiler hvis behandling ikke finnes`() {
        val service = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
                on { hent(any()) } doReturn null
            },
        )

        val svar = service.søknadsbehandlingService.returner(
            SøknadsbehandlingService.ReturnerBehandlingRequest
                (SøknadsbehandlingId(UUID.randomUUID()), NavIdentBruker.Saksbehandler("Ident")),
        )
        svar shouldBe KunneIkkeReturnereSøknadsbehandling.FantIkkeBehandling.left()
    }

    @Test
    fun `returner behandling, feiler hvis behandling er i ugyldig tilstand for retur`() {
        val service = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
                on { hent(any()) } doReturn mock<BeregnetSøknadsbehandling.Avslag>()
            },
        )

        val svar = service.søknadsbehandlingService.returner(
            SøknadsbehandlingService.ReturnerBehandlingRequest
                (SøknadsbehandlingId(UUID.randomUUID()), NavIdentBruker.Saksbehandler("Ident")),
        )
        svar shouldBe KunneIkkeReturnereSøknadsbehandling.FeilSaksbehandler.left()
    }

    @Test
    fun `returner behandling, feiler hvis det er feil saksbehandler`() {
        val (_, søknadsmock) = søknadsbehandlingTilAttesteringAvslagMedBeregning(saksbehandler = NavIdentBruker.Saksbehandler("Annen saksbehandler"))

        val service = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = mock<SøknadsbehandlingRepo> {
                on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
                on { hent(any()) } doReturn søknadsmock
            },
        )
        val svar = service.søknadsbehandlingService.returner(
            SøknadsbehandlingService.ReturnerBehandlingRequest
                (SøknadsbehandlingId(UUID.randomUUID()), NavIdentBruker.Saksbehandler("Feil Saksbehandler")),
        )
        svar shouldBe KunneIkkeReturnereSøknadsbehandling.FeilSaksbehandler.left()
    }

    @Test
    fun `happy case`() {
        val søknadsbehandlingId = SøknadsbehandlingId(UUID.randomUUID())
        val riktigSaksbehandler = NavIdentBruker.Saksbehandler("Ident")
        val (_, søknadsmock) = søknadsbehandlingTilAttesteringAvslagMedBeregning(saksbehandler = riktigSaksbehandler)

        val søknadsbehandlingRepo = mock<SøknadsbehandlingRepo> {
            on { defaultTransactionContext() } doReturn TestSessionFactory.transactionContext
            on { lagre(any(), any()) } doAnswer { mock() }
            on { hent(søknadsbehandlingId) } doReturn søknadsmock
        }
        val service = SøknadsbehandlingServiceAndMocks(
            søknadsbehandlingRepo = søknadsbehandlingRepo,
            oppgaveService = mock {
                on { oppdaterOppgave(any(), any()) } doAnswer { mock(OppgaveHttpKallResponse::class.java).right() }
            },
        )
        val svar = service.søknadsbehandlingService.returner(
            SøknadsbehandlingService.ReturnerBehandlingRequest
                (SøknadsbehandlingId(søknadsbehandlingId.value), riktigSaksbehandler),
        )
        svar.shouldBeRight()

        verify(søknadsbehandlingRepo, times(1)).hent(søknadsbehandlingId)
    }
}
