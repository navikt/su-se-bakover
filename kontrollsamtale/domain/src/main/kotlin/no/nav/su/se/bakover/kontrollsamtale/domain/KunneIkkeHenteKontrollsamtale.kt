package no.nav.su.se.bakover.kontrollsamtale.domain

sealed interface KunneIkkeHenteKontrollsamtale {
    object FantIkkePlanlagtKontrollsamtale : KunneIkkeHenteKontrollsamtale
    object KunneIkkeHenteKontrollsamtaler : KunneIkkeHenteKontrollsamtale
}
