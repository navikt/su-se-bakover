package tilbakekreving.domain.underkjennelse

import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeUnderkjenne {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeUnderkjenne
    data object UlikVersjon : KunneIkkeUnderkjenne
}
