package no.nav.su.se.bakover.domain.klage

import kotlin.reflect.KClass

sealed interface KunneIkkeLageBrevKommandoForKlage {
    data object FeilVedHentingAvVedtaksbrevDato : KunneIkkeLageBrevKommandoForKlage
    data class UgyldigTilstand(val fra: KClass<out Klage>) : KunneIkkeLageBrevKommandoForKlage
}
