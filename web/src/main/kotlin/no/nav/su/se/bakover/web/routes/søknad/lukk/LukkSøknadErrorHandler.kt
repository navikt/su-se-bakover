package no.nav.su.se.bakover.web.routes.søknad.lukk

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad
import no.nav.su.se.bakover.service.søknad.lukk.LukketSøknad
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson

internal object LukkSøknadErrorHandler {
    fun kunneIkkeLukkeSøknadResponse(request: LukkSøknadRequest, error: KunneIkkeLukkeSøknad): Resultat = when (error) {
        is KunneIkkeLukkeSøknad.SøknadErAlleredeLukket ->
            HttpStatusCode.BadRequest.message("Søknad er allerede trukket")
        is KunneIkkeLukkeSøknad.SøknadHarEnBehandling ->
            HttpStatusCode.BadRequest.message("Søknaden har en behandling")
        is KunneIkkeLukkeSøknad.FantIkkeSøknad ->
            HttpStatusCode.NotFound.message("Fant ikke søknad for ${request.søknadId}")
        is KunneIkkeLukkeSøknad.KunneIkkeJournalføreBrev ->
            HttpStatusCode.InternalServerError.message("Kunne ikke journalføre brev")
        is KunneIkkeLukkeSøknad.UgyldigDato ->
            HttpStatusCode.InternalServerError.message("Kan ikke trekke søknad før den er opprettet eller frem i tid")
    }

    fun lukketSøknadResponse(error: LukketSøknad) = when (error) {
        is LukketSøknad.UtenMangler,
        is LukketSøknad.MedMangler.KunneIkkeDistribuereBrev,
        is LukketSøknad.MedMangler.KunneIkkeLukkeOppgave -> Resultat.json(HttpStatusCode.OK, serialize((error.sak.toJson())))
    }
}
