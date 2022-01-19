package no.nav.su.se.bakover.web.routes.revurdering

import no.nav.su.se.bakover.domain.avkorting.AvkortingVedRevurdering
import no.nav.su.se.bakover.web.routes.søknadsbehandling.SimuleringJson
import no.nav.su.se.bakover.web.routes.toJson

internal fun AvkortingVedRevurdering.toJson(): SimuleringJson? {
    return when (this) {
        is AvkortingVedRevurdering.DelvisHåndtert.AnnullerUtestående -> {
            null
        }
        is AvkortingVedRevurdering.DelvisHåndtert.IngenUtestående -> {
            null
        }
        is AvkortingVedRevurdering.Håndtert.AnnullerUtestående -> {
            null
        }
        is AvkortingVedRevurdering.Håndtert.IngenNyEllerUtestående -> {
            null
        }
        is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarsel -> {
            avkortingsvarsel.toJson()
        }
        is AvkortingVedRevurdering.Håndtert.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
            avkortingsvarsel.toJson()
        }
        is AvkortingVedRevurdering.Uhåndtert.IngenUtestående -> {
            null
        }
        is AvkortingVedRevurdering.Uhåndtert.UteståendeAvkorting -> {
            null
        }
        is AvkortingVedRevurdering.Iverksatt.AnnullerUtestående -> {
            null
        }
        is AvkortingVedRevurdering.Iverksatt.IngenNyEllerUtestående -> {
            null
        }
        is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarsel -> {
            avkortingsvarsel.toJson()
        }
        is AvkortingVedRevurdering.Iverksatt.OpprettNyttAvkortingsvarselOgAnnullerUtestående -> {
            avkortingsvarsel.toJson()
        }
        is AvkortingVedRevurdering.DelvisHåndtert.KanIkkeHåndtere -> {
            null
        }
        is AvkortingVedRevurdering.Håndtert.KanIkkeHåndteres -> {
            null
        }
        is AvkortingVedRevurdering.Iverksatt.KanIkkeHåndteres -> {
            null
        }
        is AvkortingVedRevurdering.Uhåndtert.KanIkkeHåndtere -> {
            null
        }
    }
}
