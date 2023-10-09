package tilbakekreving.application.service.consumer

import person.domain.KunneIkkeHentePerson

sealed interface KunneIkkeOppretteOppgave {
    data class FeilVedHentingAvPerson(val feil: KunneIkkeHentePerson) : KunneIkkeOppretteOppgave
    data object FeilVedOpprettelseAvOppgave : KunneIkkeOppretteOppgave
}
