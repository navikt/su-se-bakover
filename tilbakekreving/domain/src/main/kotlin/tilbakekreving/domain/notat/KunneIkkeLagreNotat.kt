package tilbakekreving.domain.notat

import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeLagreNotat {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeLagreNotat
}
