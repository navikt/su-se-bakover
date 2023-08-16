package no.nav.su.se.bakover.kontrollsamtale.domain

import no.nav.su.se.bakover.domain.dokument.KunneIkkeLageDokument

sealed interface KunneIkkeKalleInnTilKontrollsamtale {
    data object FantIkkeSak : KunneIkkeKalleInnTilKontrollsamtale
    data object FantIkkePerson : KunneIkkeKalleInnTilKontrollsamtale
    data class KunneIkkeGenerereDokument(
        val underliggende: KunneIkkeLageDokument,
    ) : KunneIkkeKalleInnTilKontrollsamtale
    data object KunneIkkeKalleInn : KunneIkkeKalleInnTilKontrollsamtale
    data object FantIkkeGjeldendeStønadsperiode : KunneIkkeKalleInnTilKontrollsamtale
}
