package no.nav.su.se.bakover.domain.revurdering.brev

import no.nav.su.se.bakover.domain.revurdering.Revurdering

sealed interface KunneIkkeLeggeTilBrevvalg {

    object FantIkkeRevurdering : KunneIkkeLeggeTilBrevvalg
    data class Feil(val feil: Revurdering.KunneIkkeLeggeTilBrevvalg) : KunneIkkeLeggeTilBrevvalg
}
