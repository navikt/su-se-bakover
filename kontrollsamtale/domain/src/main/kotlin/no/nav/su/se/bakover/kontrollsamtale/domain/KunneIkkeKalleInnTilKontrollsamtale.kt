package no.nav.su.se.bakover.kontrollsamtale.domain

sealed interface KunneIkkeKalleInnTilKontrollsamtale {
    data object FantIkkeSak : KunneIkkeKalleInnTilKontrollsamtale
    data object FantIkkePerson : KunneIkkeKalleInnTilKontrollsamtale
    data object KunneIkkeGenerereDokument : KunneIkkeKalleInnTilKontrollsamtale
    data object KunneIkkeKalleInn : KunneIkkeKalleInnTilKontrollsamtale
    data object FantIkkeGjeldendeStønadsperiode : KunneIkkeKalleInnTilKontrollsamtale
}
