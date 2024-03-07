package no.nav.su.se.bakover.domain.søknadsbehandling.avbryt

sealed interface KunneIkkeLukkeSøknadsbehandling {
    data object KanIkkeLukkeEnAlleredeLukketSøknadsbehandling : KunneIkkeLukkeSøknadsbehandling

    data object KanIkkeLukkeEnIverksattSøknadsbehandling : KunneIkkeLukkeSøknadsbehandling

    data object KanIkkeLukkeEnSøknadsbehandlingTilAttestering : KunneIkkeLukkeSøknadsbehandling
}
