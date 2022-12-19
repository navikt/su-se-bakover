package no.nav.su.se.bakover.web.routes.søknadsbehandling.opprett

import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.domain.Sak

internal fun Sak.KunneIkkeOppretteSøknadsbehandling.tilResultat() = when (this) {
    Sak.KunneIkkeOppretteSøknadsbehandling.ErLukket -> Feilresponser.søknadErLukket
    Sak.KunneIkkeOppretteSøknadsbehandling.FinnesAlleredeSøknadsehandlingForSøknad -> Feilresponser.søknadHarBehandlingFraFør
    Sak.KunneIkkeOppretteSøknadsbehandling.HarÅpenBehandling -> Feilresponser.harAlleredeÅpenBehandling
    Sak.KunneIkkeOppretteSøknadsbehandling.ManglerOppgave -> Feilresponser.søknadManglerOppgave
}
