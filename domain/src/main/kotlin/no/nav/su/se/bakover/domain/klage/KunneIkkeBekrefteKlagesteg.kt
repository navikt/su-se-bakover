package no.nav.su.se.bakover.domain.klage

import kotlin.reflect.KClass

sealed interface KunneIkkeBekrefteKlagesteg {
    data object FantIkkeKlage : KunneIkkeBekrefteKlagesteg
    data class UgyldigTilstand(val fra: KClass<out Klage>, val til: KClass<out Klage>) :
        KunneIkkeBekrefteKlagesteg
}
