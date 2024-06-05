package tilbakekreving.domain.notat

import tilgangstyring.domain.IkkeTilgangTilSak

sealed interface KunneIkkeOppdatereNotat {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeOppdatereNotat
}
