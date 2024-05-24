package no.nav.su.se.bakover.kontrollsamtale.domain.annuller

sealed interface KunneIkkeAnnullereKontrollsamtale {
    data object UgyldigStatusovergang : KunneIkkeAnnullereKontrollsamtale
}
