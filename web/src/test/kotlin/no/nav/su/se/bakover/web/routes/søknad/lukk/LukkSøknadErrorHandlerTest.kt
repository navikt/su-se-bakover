package no.nav.su.se.bakover.web.routes.søknad.lukk

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.oktober
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLukkeSøknadsbehandling
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad
import no.nav.su.se.bakover.web.errorJson
import org.junit.jupiter.api.Test
import java.util.UUID

internal class LukkSøknadErrorHandlerTest {

    @Test
    fun `returnerer feilmelding basert på oppstått feilsituasjon`() {
        val medBrevRequest = LukkSøknadRequest.MedBrev.TrekkSøknad(
            søknadId = UUID.randomUUID(),
            saksbehandler = NavIdentBruker.Saksbehandler(navIdent = "Z123"),
            trukketDato = 1.oktober(2020),
        )
        assertSoftly {

            LukkSøknadErrorHandler.kunneIkkeLukkeSøknadResponse(
                request = medBrevRequest,
                error = KunneIkkeLukkeSøknad.SøknadErAlleredeLukket,
            ) shouldBe HttpStatusCode.BadRequest.errorJson(
                "Søknad med id ${medBrevRequest.søknadId} er allerede lukket",
                "søknad_allerede_lukket"
            )

            LukkSøknadErrorHandler.kunneIkkeLukkeSøknadResponse(
                request = medBrevRequest,
                error = KunneIkkeLukkeSøknad.BehandlingErIFeilTilstand(KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnAlleredeLukketSøknadsbehandling),
            ) shouldBe HttpStatusCode.BadRequest.errorJson(
                "Behandlingen tilknyttet søknad med id ${medBrevRequest.søknadId} er allerede lukket og kan derfor ikke lukkes",
                "kan_ikke_lukke_en_allerede_lukket_søknadsbehandling"
            )

            LukkSøknadErrorHandler.kunneIkkeLukkeSøknadResponse(
                request = medBrevRequest,
                error = KunneIkkeLukkeSøknad.BehandlingErIFeilTilstand(KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnIverksattSøknadsbehandling),
            ) shouldBe HttpStatusCode.BadRequest.errorJson(
                "Behandlingen tilknyttet søknad med id ${medBrevRequest.søknadId} er iverksatt og kan derfor ikke lukkes",
                "kan_ikke_lukke_en_iverksatt_søknadsbehandling"
            )

            LukkSøknadErrorHandler.kunneIkkeLukkeSøknadResponse(
                request = medBrevRequest,
                error = KunneIkkeLukkeSøknad.BehandlingErIFeilTilstand(KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnSøknadsbehandlingTilAttestering),
            ) shouldBe HttpStatusCode.BadRequest.errorJson(
                "Behandlingen tilknyttet søknad med id ${medBrevRequest.søknadId} er til attestering og kan derfor ikke lukkes",
                "kan_ikke_lukke_en_søknadsbehandling_til_attestering"
            )

            LukkSøknadErrorHandler.kunneIkkeLukkeSøknadResponse(
                medBrevRequest,
                KunneIkkeLukkeSøknad.FantIkkeSøknad,
            ) shouldBe HttpStatusCode.NotFound.errorJson(
                "Fant ikke søknad med id ${medBrevRequest.søknadId}",
                "fant_ikke_søknad"
            )

            LukkSøknadErrorHandler.kunneIkkeLukkeSøknadResponse(
                request = medBrevRequest,
                error = KunneIkkeLukkeSøknad.UgyldigTrukketDato,
            ) shouldBe HttpStatusCode.BadRequest.errorJson(
                "Ugyldig lukket dato. Dato må være etter opprettet og kan ikke være frem i tid",
                "ugyldig_dato"
            )

            LukkSøknadErrorHandler.kunneIkkeLukkeSøknadResponse(
                request = medBrevRequest,
                error = KunneIkkeLukkeSøknad.KunneIkkeGenerereDokument,
            ) shouldBe HttpStatusCode.InternalServerError.errorJson(
                "Feil ved generering av dokument",
                "feil_ved_generering_av_dokument",
            )
        }
    }
}
