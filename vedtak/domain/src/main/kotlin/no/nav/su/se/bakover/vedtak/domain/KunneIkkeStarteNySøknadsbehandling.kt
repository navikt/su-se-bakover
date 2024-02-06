package no.nav.su.se.bakover.vedtak.domain

import behandling.søknadsbehandling.domain.KunneIkkeOppretteSøknadsbehandling

sealed interface KunneIkkeStarteNySøknadsbehandling {
    data object FantIkkeVedtak : KunneIkkeStarteNySøknadsbehandling
    data object FantIkkeSak : KunneIkkeStarteNySøknadsbehandling
    data object VedtakErIkkeAvslag : KunneIkkeStarteNySøknadsbehandling
    data class FeilVedOpprettelseAvSøknadsbehandling(val feil: KunneIkkeOppretteSøknadsbehandling) : KunneIkkeStarteNySøknadsbehandling
}
