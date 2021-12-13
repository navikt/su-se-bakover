package no.nav.su.se.bakover.domain.klage

sealed class KunneIkkeLageBrevForKlage {
    object FantIkkePerson : KunneIkkeLageBrevForKlage()
    object FantIkkeSaksbehandler : KunneIkkeLageBrevForKlage()
    object FantIkkeVedtakKnyttetTilKlagen : KunneIkkeLageBrevForKlage()
    object KunneIkkeGenererePDF : KunneIkkeLageBrevForKlage()
}
