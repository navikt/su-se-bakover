package tilbakekreving.domain.opprett

import person.domain.KunneIkkeHentePerson
import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeOppretteTilbakekrevingsbehandling {
    data object IngenÅpneKravgrunnlag : KunneIkkeOppretteTilbakekrevingsbehandling
    data object FinnesAlleredeEnÅpenBehandling : KunneIkkeOppretteTilbakekrevingsbehandling
    data class FeilVedHentingAvPerson(val feil: KunneIkkeHentePerson) : KunneIkkeOppretteTilbakekrevingsbehandling

    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeOppretteTilbakekrevingsbehandling
    data object FeilVedOpprettelseAvOppgave : KunneIkkeOppretteTilbakekrevingsbehandling
}
