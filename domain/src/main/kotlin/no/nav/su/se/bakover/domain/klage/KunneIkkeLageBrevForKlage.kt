package no.nav.su.se.bakover.domain.klage

import kotlin.reflect.KClass

sealed interface KunneIkkeLageBrevForKlage {
    data object FantIkkePerson : KunneIkkeLageBrevForKlage
    data object FantIkkeSaksbehandler : KunneIkkeLageBrevForKlage
    data object FantIkkeVedtakKnyttetTilKlagen : KunneIkkeLageBrevForKlage
    data object KunneIkkeGenererePDF : KunneIkkeLageBrevForKlage
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeLageBrevForKlage
    data class FeilVedBrevRequest(val feil: KunneIkkeLageBrevRequestForKlage) : KunneIkkeLageBrevForKlage
}
