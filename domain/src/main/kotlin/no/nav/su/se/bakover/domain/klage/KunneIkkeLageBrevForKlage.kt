package no.nav.su.se.bakover.domain.klage

import kotlin.reflect.KClass

sealed class KunneIkkeLageBrevForKlage {
    object FantIkkePerson : KunneIkkeLageBrevForKlage()
    object FantIkkeSaksbehandler : KunneIkkeLageBrevForKlage()
    object FantIkkeVedtakKnyttetTilKlagen : KunneIkkeLageBrevForKlage()
    object KunneIkkeGenererePDF : KunneIkkeLageBrevForKlage()
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeLageBrevForKlage()
}
