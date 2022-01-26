package no.nav.su.se.bakover.web.routes.søknadsbehandling

import no.nav.su.se.bakover.domain.avkorting.AvkortingVedSøknadsbehandling
import no.nav.su.se.bakover.web.routes.toJson

internal fun AvkortingVedSøknadsbehandling.toJson(): SimuleringJson? {
    return when (this) {
        is AvkortingVedSøknadsbehandling.Håndtert.AvkortUtestående -> {
            avkortingsvarsel.toJson()
        }
        is AvkortingVedSøknadsbehandling.Håndtert.IngenUtestående -> {
            null
        }
        is AvkortingVedSøknadsbehandling.Iverksatt.AvkortUtestående -> {
            avkortingsvarsel.toJson()
        }
        is AvkortingVedSøknadsbehandling.Iverksatt.IngenUtestående -> {
            null
        }
        is AvkortingVedSøknadsbehandling.Uhåndtert.IngenUtestående -> {
            null
        }
        is AvkortingVedSøknadsbehandling.Uhåndtert.UteståendeAvkorting -> {
            avkortingsvarsel.toJson()
        }
        is AvkortingVedSøknadsbehandling.Håndtert.KanIkkeHåndtere -> {
            null
        }
        is AvkortingVedSøknadsbehandling.Iverksatt.KanIkkeHåndtere -> {
            null
        }
        is AvkortingVedSøknadsbehandling.Uhåndtert.KanIkkeHåndtere -> {
            null
        }
    }
}
