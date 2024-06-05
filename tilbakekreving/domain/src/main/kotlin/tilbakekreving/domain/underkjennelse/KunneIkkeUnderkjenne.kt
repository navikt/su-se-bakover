package tilbakekreving.domain.underkjennelse

import tilgangstyring.domain.IkkeTilgangTilSak

sealed interface KunneIkkeUnderkjenne {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeUnderkjenne
    data object UlikVersjon : KunneIkkeUnderkjenne
}
