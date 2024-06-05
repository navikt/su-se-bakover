package tilbakekreving.domain.avbrudd

import tilgangstyring.domain.IkkeTilgangTilSak

sealed interface KunneIkkeAvbryte {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeAvbryte
    data object UlikVersjon : KunneIkkeAvbryte
}
