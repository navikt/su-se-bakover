package tilbakekreving.domain.hent

import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeHenteÅpentKravgrunnlag {
    data object FinnesIngenÅpneKravgrunnlag : KunneIkkeHenteÅpentKravgrunnlag
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeHenteÅpentKravgrunnlag
}
