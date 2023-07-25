package no.nav.su.se.bakover.domain.søknadsbehandling.tilAttestering

import no.nav.su.se.bakover.domain.søknadsbehandling.Søknadsbehandling
import no.nav.su.se.bakover.domain.søknadsbehandling.SøknadsbehandlingTilAttestering
import kotlin.reflect.KClass

sealed interface KunneIkkeSendeSøknadsbehandlingTilAttestering {
    data object KunneIkkeFinneAktørId : KunneIkkeSendeSøknadsbehandlingTilAttestering
    data object KunneIkkeOppretteOppgave : KunneIkkeSendeSøknadsbehandlingTilAttestering
    data object InneholderUfullstendigBosituasjon : KunneIkkeSendeSøknadsbehandlingTilAttestering

    data class UgyldigTilstand(
        val fra: KClass<out Søknadsbehandling>,
        val til: KClass<out SøknadsbehandlingTilAttestering> = SøknadsbehandlingTilAttestering::class,
    ) : KunneIkkeSendeSøknadsbehandlingTilAttestering
}
