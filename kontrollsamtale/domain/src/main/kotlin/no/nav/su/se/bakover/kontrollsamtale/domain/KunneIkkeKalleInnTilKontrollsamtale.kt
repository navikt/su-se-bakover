package no.nav.su.se.bakover.kontrollsamtale.domain

import dokument.domain.KunneIkkeLageDokument

sealed interface KunneIkkeKalleInnTilKontrollsamtale {
    data object FantIkkePerson : KunneIkkeKalleInnTilKontrollsamtale
    data class KunneIkkeGenerereDokument(
        val underliggende: KunneIkkeLageDokument,
    ) : KunneIkkeKalleInnTilKontrollsamtale
    data object KunneIkkeKalleInn : KunneIkkeKalleInnTilKontrollsamtale
    data object FantIkkeGjeldendeStønadsperiode : KunneIkkeKalleInnTilKontrollsamtale
    data object PersonErDød : KunneIkkeKalleInnTilKontrollsamtale
    data object SakErOpphørt : KunneIkkeKalleInnTilKontrollsamtale
    data object SakErStanset : KunneIkkeKalleInnTilKontrollsamtale
    data object UgyldigTilstand : KunneIkkeKalleInnTilKontrollsamtale
}
