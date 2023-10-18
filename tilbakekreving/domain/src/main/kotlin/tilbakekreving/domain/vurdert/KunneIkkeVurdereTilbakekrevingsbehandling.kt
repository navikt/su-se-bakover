package tilbakekreving.domain.vurdert

import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeVurdereTilbakekrevingsbehandling {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeVurdereTilbakekrevingsbehandling
    data object UlikVersjon : KunneIkkeVurdereTilbakekrevingsbehandling
}
