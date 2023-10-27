package tilbakekreving.domain.avbrutt

import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeAvbryte {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeAvbryte
    data object UlikVersjon : KunneIkkeAvbryte
}
