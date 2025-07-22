package vedtak.domain

import behandling.søknadsbehandling.domain.KunneIkkeOppretteSøknadsbehandling
import person.domain.KunneIkkeHentePerson

sealed interface KunneIkkeStarteNySøknadsbehandling {
    data object FantIkkeVedtak : KunneIkkeStarteNySøknadsbehandling
    data object FantIkkeSak : KunneIkkeStarteNySøknadsbehandling
    data object VedtakErIkkeAvslag : KunneIkkeStarteNySøknadsbehandling
    data class FeilVedOpprettelseAvSøknadsbehandling(val feil: KunneIkkeOppretteSøknadsbehandling) : KunneIkkeStarteNySøknadsbehandling

    data class FeilVedHentingAvPersonForOpprettelseAvOppgave(val feil: KunneIkkeHentePerson) : KunneIkkeStarteNySøknadsbehandling

    data object ÅpenBehandlingFinnes : KunneIkkeStarteNySøknadsbehandling

    // TODO - ta inn feilmelding - må flytte ting til oppgave-modulen
    data object FeilVedOpprettelseAvOppgave : KunneIkkeStarteNySøknadsbehandling

    data object MåHaGyldingOmgjøringsgrunn : KunneIkkeStarteNySøknadsbehandling
    data object UgyldigRevurderingsÅrsak : KunneIkkeStarteNySøknadsbehandling
}
