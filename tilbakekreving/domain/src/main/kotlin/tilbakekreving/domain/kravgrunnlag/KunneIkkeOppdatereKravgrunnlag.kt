package tilbakekreving.domain.kravgrunnlag

import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeOppdatereKravgrunnlag {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeOppdatereKravgrunnlag
    data object FantIkkeUteståendeKravgrunnlag : KunneIkkeOppdatereKravgrunnlag
}
