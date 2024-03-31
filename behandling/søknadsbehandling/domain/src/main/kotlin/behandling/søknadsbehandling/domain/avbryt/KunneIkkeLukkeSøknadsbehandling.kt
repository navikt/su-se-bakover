package behandling.søknadsbehandling.domain.avbryt

sealed interface KunneIkkeLukkeSøknadsbehandling {
    data object KanIkkeLukkeEnAlleredeLukketSøknadsbehandling : KunneIkkeLukkeSøknadsbehandling

    data object KanIkkeLukkeEnIverksattSøknadsbehandling : KunneIkkeLukkeSøknadsbehandling

    data object KanIkkeLukkeEnSøknadsbehandlingTilAttestering : KunneIkkeLukkeSøknadsbehandling
}
