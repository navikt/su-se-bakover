package behandling.søknadsbehandling.presentation

import behandling.søknadsbehandling.domain.KunneIkkeOppretteSøknadsbehandling
import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.errorJson

fun KunneIkkeOppretteSøknadsbehandling.tilResultat() = when (this) {
    KunneIkkeOppretteSøknadsbehandling.ErLukket -> Feilresponser.søknadErLukket
    KunneIkkeOppretteSøknadsbehandling.ManglerOppgave -> Feilresponser.søknadManglerOppgave
    KunneIkkeOppretteSøknadsbehandling.HarÅpenSøknadsbehandling -> HttpStatusCode.BadRequest.errorJson(
        "Det finnes en eksisterende åpen søknadsbehandling. Iverksett eller lukk denne først.",
        "finnes_åpen_søknadsbehandling",
    )
}
