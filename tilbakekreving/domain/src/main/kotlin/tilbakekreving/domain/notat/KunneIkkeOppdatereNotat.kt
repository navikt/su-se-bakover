package tilbakekreving.domain.notat

import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeOppdatereNotat {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeOppdatereNotat
}
