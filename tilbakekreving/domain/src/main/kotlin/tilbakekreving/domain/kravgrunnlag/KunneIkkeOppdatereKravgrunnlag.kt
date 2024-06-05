package tilbakekreving.domain.kravgrunnlag

import tilgangstyring.domain.IkkeTilgangTilSak

sealed interface KunneIkkeOppdatereKravgrunnlag {
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeOppdatereKravgrunnlag
    data object FantIkkeUteståendeKravgrunnlag : KunneIkkeOppdatereKravgrunnlag
}
