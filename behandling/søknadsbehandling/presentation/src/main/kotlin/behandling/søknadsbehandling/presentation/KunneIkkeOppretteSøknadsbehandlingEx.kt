package behandling.søknadsbehandling.presentation

import behandling.søknadsbehandling.domain.KunneIkkeStarteSøknadsbehandling
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser

fun KunneIkkeStarteSøknadsbehandling.tilResultat() = when (this) {
    KunneIkkeStarteSøknadsbehandling.ErLukket -> Feilresponser.søknadErLukket
    KunneIkkeStarteSøknadsbehandling.ManglerOppgave -> Feilresponser.søknadManglerOppgave
    KunneIkkeStarteSøknadsbehandling.FeilVedOpprettingAvOppgave -> Feilresponser.kunneIkkeOppretteOppgave
    KunneIkkeStarteSøknadsbehandling.BehandlingErAlleredePåbegynt -> Feilresponser.behandlingErAlleredePåbegynt
    KunneIkkeStarteSøknadsbehandling.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
}
