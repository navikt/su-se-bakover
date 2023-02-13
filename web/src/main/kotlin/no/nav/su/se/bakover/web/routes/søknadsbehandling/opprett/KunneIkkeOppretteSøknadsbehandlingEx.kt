package no.nav.su.se.bakover.web.routes.søknadsbehandling.opprett

import io.ktor.http.HttpStatusCode
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.domain.Sak

internal fun Sak.KunneIkkeOppretteSøknadsbehandling.tilResultat() = when (this) {
    Sak.KunneIkkeOppretteSøknadsbehandling.ErLukket -> Feilresponser.søknadErLukket
    Sak.KunneIkkeOppretteSøknadsbehandling.FinnesAlleredeSøknadsehandlingForSøknad -> Feilresponser.søknadHarBehandlingFraFør
    Sak.KunneIkkeOppretteSøknadsbehandling.ManglerOppgave -> Feilresponser.søknadManglerOppgave
    Sak.KunneIkkeOppretteSøknadsbehandling.HarÅpenSøknadsbehandling -> HttpStatusCode.BadRequest.errorJson(
        "Det finnes en eksisterende åpen søknadsbehandling. Iverksett eller lukk denne først.",
        "finnes_åpen_søknadsbehandling",
    )
}
