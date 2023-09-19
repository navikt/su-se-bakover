package tilbakekreving.domain.opprett

import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeOppretteTilbakekrevingsbehandling {
    data object IngenÅpneKravgrunnlag : KunneIkkeOppretteTilbakekrevingsbehandling
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeOppretteTilbakekrevingsbehandling
}
