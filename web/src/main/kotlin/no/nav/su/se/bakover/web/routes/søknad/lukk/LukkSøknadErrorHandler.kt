package no.nav.su.se.bakover.web.routes.søknad.lukk

import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.domain.søknadsbehandling.KunneIkkeLukkeSøknadsbehandling
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad.FantIkkePerson
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad.FantIkkeSøknad
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad.SøknadErAlleredeLukket
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad.UgyldigTrukketDato
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.errorJson
import no.nav.su.se.bakover.web.routes.Feilresponser
import no.nav.su.se.bakover.web.routes.Feilresponser.feilVedGenereringAvDokument

internal object LukkSøknadErrorHandler {
    fun kunneIkkeLukkeSøknadResponse(request: LukkSøknadRequest, error: KunneIkkeLukkeSøknad): Resultat {
        val søknadId = request.søknadId
        return when (error) {
            is SøknadErAlleredeLukket -> BadRequest.errorJson(
                "Søknad med id $søknadId er allerede lukket",
                "søknad_allerede_lukket",
            )
            is FantIkkeSøknad -> NotFound.errorJson("Fant ikke søknad med id $søknadId", "fant_ikke_søknad")
            is UgyldigTrukketDato -> BadRequest.errorJson(
                "Ugyldig lukket dato. Dato må være etter opprettet og kan ikke være frem i tid",
                "ugyldig_dato",
            )
            is FantIkkePerson -> Feilresponser.fantIkkePerson
            is KunneIkkeLukkeSøknad.SøknadManglerOppgave -> InternalServerError.errorJson(
                "Søknad med id $søknadId mangler oppgave",
                "søknad_mangler_oppgave",
            )
            KunneIkkeLukkeSøknad.KunneIkkeGenerereDokument -> feilVedGenereringAvDokument
            is KunneIkkeLukkeSøknad.BehandlingErIFeilTilstand -> when (error.feil) {
                KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnAlleredeLukketSøknadsbehandling -> BadRequest.errorJson(
                    "Behandlingen tilknyttet søknad med id $søknadId er allerede lukket og kan derfor ikke lukkes",
                    "kan_ikke_lukke_en_allerede_lukket_søknadsbehandling",
                )
                KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnIverksattSøknadsbehandling -> BadRequest.errorJson(
                    "Behandlingen tilknyttet søknad med id $søknadId er iverksatt og kan derfor ikke lukkes",
                    "kan_ikke_lukke_en_iverksatt_søknadsbehandling",
                )
                KunneIkkeLukkeSøknadsbehandling.KanIkkeLukkeEnSøknadsbehandlingTilAttestering -> BadRequest.errorJson(
                    "Behandlingen tilknyttet søknad med id $søknadId er til attestering og kan derfor ikke lukkes",
                    "kan_ikke_lukke_en_søknadsbehandling_til_attestering",
                )
            }
            KunneIkkeLukkeSøknad.FantIkkeSak -> NotFound.errorJson("Fant ikke sak", "fant_ikke_sak")
        }
    }
}
