package tilbakekreving.domain.opprett

import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeOppretteTilbakekrevingsbehandling {
    data object IngenÅpneKravgrunnlag : KunneIkkeOppretteTilbakekrevingsbehandling
    data object FinnesAlleredeEnÅpenBehandling : KunneIkkeOppretteTilbakekrevingsbehandling
    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeOppretteTilbakekrevingsbehandling
}
