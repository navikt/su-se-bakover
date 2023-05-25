package no.nav.su.se.bakover.web.routes.søknadsbehandling

import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.web.routes.toJson

internal fun AvkortingVedSøknadsbehandling.toJson(): SimuleringJson? {
    return when (this) {
        AvkortingVedSøknadsbehandling.IkkeVurdert -> null
        AvkortingVedSøknadsbehandling.IngenAvkorting -> null
        is AvkortingVedSøknadsbehandling.Avkortet -> this.avkortingsvarsel.toJson()
        is AvkortingVedSøknadsbehandling.SkalAvkortes -> this.avkortingsvarsel.toJson()
    }
}
