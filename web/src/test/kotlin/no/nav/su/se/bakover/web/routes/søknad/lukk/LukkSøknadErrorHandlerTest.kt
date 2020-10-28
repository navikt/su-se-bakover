package no.nav.su.se.bakover.web.routes.søknad.lukk

import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad
import no.nav.su.se.bakover.web.Resultat
import org.junit.jupiter.api.Test
import java.util.UUID

internal class LukkSøknadErrorHandlerTest {
    @Test
    fun `returnerer feilmelding basert på oppstått feilsituasjon`() {
        val medBrevRequest = LukkSøknadRequest.MedBrev.TrekkSøknad(
            søknadId = UUID.randomUUID(),
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "Z123"),
            trukketDato = 1.oktober(2020)
        )
        LukkSøknadErrorHandler.handle(
            request = medBrevRequest,
            error = KunneIkkeLukkeSøknad.SøknadErAlleredeLukket
        ) shouldBe Resultat.message(
            HttpStatusCode.BadRequest,
            "Søknad er allerede trukket"
        )

        LukkSøknadErrorHandler.handle(
            request = medBrevRequest,
            error = KunneIkkeLukkeSøknad.SøknadHarEnBehandling
        ) shouldBe Resultat.message(
            HttpStatusCode.BadRequest,
            "Søknaden har en behandling"
        )

        LukkSøknadErrorHandler.handle(medBrevRequest, KunneIkkeLukkeSøknad.FantIkkeSøknad) shouldBe Resultat.message(
            httpCode = HttpStatusCode.NotFound,
            message = "Fant ikke søknad for ${medBrevRequest.søknadId}"
        )

        LukkSøknadErrorHandler.handle(
            request = medBrevRequest,
            error = KunneIkkeLukkeSøknad.KunneIkkeJournalføreBrev
        ) shouldBe Resultat.message(
            HttpStatusCode.InternalServerError,
            "Kunne ikke journalføre brev"
        )

        LukkSøknadErrorHandler.handle(
            request = medBrevRequest,
            error = KunneIkkeLukkeSøknad.KunneIkkeDistribuereBrev
        ) shouldBe Resultat.message(
            HttpStatusCode.InternalServerError,
            "Kunne distribuere brev"
        )
    }
}
