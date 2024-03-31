package no.nav.su.se.bakover.domain.klage

import kotlin.reflect.KClass

sealed interface KunneIkkeLeggeTilNyKlageinstansHendelse {
    data class MåVæreEnOversendtKlage(val menVar: KClass<out Klage>) : KunneIkkeLeggeTilNyKlageinstansHendelse
    data object KunneIkkeLageOppgave : KunneIkkeLeggeTilNyKlageinstansHendelse
}
