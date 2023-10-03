package tilbakekreving.domain.forhåndsvarsel

import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeForhåndsvarsle {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeForhåndsvarsle
}
