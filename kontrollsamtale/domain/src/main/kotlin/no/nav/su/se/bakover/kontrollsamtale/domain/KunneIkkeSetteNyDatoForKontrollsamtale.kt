package no.nav.su.se.bakover.kontrollsamtale.domain

sealed interface KunneIkkeSetteNyDatoForKontrollsamtale {
    data object FantIkkeSak : KunneIkkeSetteNyDatoForKontrollsamtale
    data object UgyldigStatusovergang : KunneIkkeSetteNyDatoForKontrollsamtale
    data object FantIkkeGjeldendeStønadsperiode : KunneIkkeSetteNyDatoForKontrollsamtale

    data object DatoIkkeFørsteIMåned : KunneIkkeSetteNyDatoForKontrollsamtale
}
