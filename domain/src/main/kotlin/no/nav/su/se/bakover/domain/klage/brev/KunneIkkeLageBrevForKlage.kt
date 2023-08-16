package no.nav.su.se.bakover.domain.klage.brev

import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.KunneIkkeLageBrevKommandoForKlage
import kotlin.reflect.KClass

sealed interface KunneIkkeLageBrevForKlage {
    data object FantIkkePerson : KunneIkkeLageBrevForKlage
    data object FantIkkeSaksbehandler : KunneIkkeLageBrevForKlage
    data object FantIkkeVedtakKnyttetTilKlagen : KunneIkkeLageBrevForKlage
    data object KunneIkkeGenererePDF : KunneIkkeLageBrevForKlage
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeLageBrevForKlage
    data class FeilVedBrevRequest(val feil: KunneIkkeLageBrevKommandoForKlage) : KunneIkkeLageBrevForKlage
}
