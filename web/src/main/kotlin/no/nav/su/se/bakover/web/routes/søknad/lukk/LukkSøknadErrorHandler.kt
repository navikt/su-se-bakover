package no.nav.su.se.bakover.web.routes.søknad.lukk

import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.http.HttpStatusCode.Companion.NotFound
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad.FantIkkePerson
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad.FantIkkeSøknad
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad.KunneIkkeJournalføreBrev
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad.SøknadErAlleredeLukket
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad.SøknadHarEnBehandling
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad.UgyldigDato
import no.nav.su.se.bakover.service.søknad.lukk.LukketSøknad
import no.nav.su.se.bakover.web.Resultat
import no.nav.su.se.bakover.web.message
import no.nav.su.se.bakover.web.routes.sak.SakJson.Companion.toJson

internal object LukkSøknadErrorHandler {
    fun kunneIkkeLukkeSøknadResponse(request: LukkSøknadRequest, error: KunneIkkeLukkeSøknad): Resultat = when (error) {
        is SøknadErAlleredeLukket -> BadRequest.message("Søknad er allerede trukket")
        is SøknadHarEnBehandling -> BadRequest.message("Søknaden har en behandling")
        is FantIkkeSøknad -> NotFound.message("Fant ikke søknad for ${request.søknadId}")
        is KunneIkkeJournalføreBrev -> InternalServerError.message("Kunne ikke journalføre brev")
        is UgyldigDato -> BadRequest.message("Kan ikke trekke søknad før den er opprettet eller frem i tid")
        is FantIkkePerson -> NotFound.message("Fant ikke person")
    }

    fun lukketSøknadResponse(error: LukketSøknad) = when (error) {
        is LukketSøknad.UtenMangler,
        is LukketSøknad.MedMangler.KunneIkkeDistribuereBrev,
        is LukketSøknad.MedMangler.KunneIkkeLukkeOppgave -> Resultat.json(HttpStatusCode.OK, serialize((error.sak.toJson())))
    }
}
