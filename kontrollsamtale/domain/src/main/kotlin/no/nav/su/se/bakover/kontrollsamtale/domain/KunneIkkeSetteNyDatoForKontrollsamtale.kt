package no.nav.su.se.bakover.kontrollsamtale.domain

sealed interface KunneIkkeSetteNyDatoForKontrollsamtale {
    object FantIkkeSak : KunneIkkeSetteNyDatoForKontrollsamtale
    object UgyldigStatusovergang : KunneIkkeSetteNyDatoForKontrollsamtale
    object FantIkkeGjeldendeStønadsperiode : KunneIkkeSetteNyDatoForKontrollsamtale

    object DatoIkkeFørsteIMåned : KunneIkkeSetteNyDatoForKontrollsamtale
}
