package vedtak.domain

import behandling.søknadsbehandling.domain.KunneIkkeStarteSøknadsbehandling
import person.domain.KunneIkkeHentePerson

sealed interface KunneIkkeStarteNySøknadsbehandling {
    data object FantIkkeVedtak : KunneIkkeStarteNySøknadsbehandling
    data object FantIkkeSak : KunneIkkeStarteNySøknadsbehandling
    data object VedtakErIkkeAvslag : KunneIkkeStarteNySøknadsbehandling
    data class FeilVedOpprettelseAvSøknadsbehandling(val feil: KunneIkkeStarteSøknadsbehandling) : KunneIkkeStarteNySøknadsbehandling

    data class FeilVedHentingAvPersonForOpprettelseAvOppgave(val feil: KunneIkkeHentePerson) : KunneIkkeStarteNySøknadsbehandling

    data object ÅpenBehandlingFinnes : KunneIkkeStarteNySøknadsbehandling

    // TODO - ta inn feilmelding - må flytte ting til oppgave-modulen
    data object FeilVedOpprettelseAvOppgave : KunneIkkeStarteNySøknadsbehandling

    data object MåHaGyldingOmgjøringsgrunn : KunneIkkeStarteNySøknadsbehandling
    data object KlageMåFinnesForKnytning : KunneIkkeStarteNySøknadsbehandling
    data object KlageUgyldigUUID : KunneIkkeStarteNySøknadsbehandling
    data object KlageErIkkeFerdigstilt : KunneIkkeStarteNySøknadsbehandling
    data object KlageErAlleredeKnyttetTilBehandling : KunneIkkeStarteNySøknadsbehandling
    data object UlikOmgjøringsgrunn : KunneIkkeStarteNySøknadsbehandling
    data object UgyldigRevurderingsÅrsak : KunneIkkeStarteNySøknadsbehandling
}
