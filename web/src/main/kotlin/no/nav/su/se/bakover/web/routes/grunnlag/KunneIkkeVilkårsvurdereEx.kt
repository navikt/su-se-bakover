package no.nav.su.se.bakover.web.routes.grunnlag

import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingService

internal fun SøknadsbehandlingService.KunneIkkeVilkårsvurdere.tilResultat(): Resultat = when (this) {
    SøknadsbehandlingService.KunneIkkeVilkårsvurdere.FantIkkeBehandling -> Feilresponser.fantIkkeBehandling
}
