package tilbakekreving.domain.opprettelse

import person.domain.KunneIkkeHentePerson
import tilbakekreving.domain.IkkeTilgangTilSak

sealed interface KunneIkkeOppretteTilbakekrevingsbehandling {
    data object IngenUteståendeKravgrunnlag : KunneIkkeOppretteTilbakekrevingsbehandling
    data object FinnesAlleredeEnÅpenBehandling : KunneIkkeOppretteTilbakekrevingsbehandling
    data class FeilVedHentingAvPerson(val feil: KunneIkkeHentePerson) : KunneIkkeOppretteTilbakekrevingsbehandling

    data class IkkeTilgang(val underliggende: IkkeTilgangTilSak) : KunneIkkeOppretteTilbakekrevingsbehandling
    data object FeilVedOpprettelseAvOppgave : KunneIkkeOppretteTilbakekrevingsbehandling
    data object UlikVersjon : KunneIkkeOppretteTilbakekrevingsbehandling
}
