package no.nav.su.se.bakover.kontrollsamtale.domain

sealed interface KunneIkkeKalleInnTilKontrollsamtale {
    object FantIkkeSak : KunneIkkeKalleInnTilKontrollsamtale
    object FantIkkePerson : KunneIkkeKalleInnTilKontrollsamtale
    object KunneIkkeGenerereDokument : KunneIkkeKalleInnTilKontrollsamtale
    object KunneIkkeKalleInn : KunneIkkeKalleInnTilKontrollsamtale
    object FantIkkeGjeldendeStønadsperiode : KunneIkkeKalleInnTilKontrollsamtale
}
