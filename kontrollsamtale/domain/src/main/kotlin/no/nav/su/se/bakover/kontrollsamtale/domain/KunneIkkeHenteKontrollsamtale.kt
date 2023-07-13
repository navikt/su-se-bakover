package no.nav.su.se.bakover.kontrollsamtale.domain

sealed interface KunneIkkeHenteKontrollsamtale {
    data object FantIkkePlanlagtKontrollsamtale : KunneIkkeHenteKontrollsamtale
    data object KunneIkkeHenteKontrollsamtaler : KunneIkkeHenteKontrollsamtale
}
