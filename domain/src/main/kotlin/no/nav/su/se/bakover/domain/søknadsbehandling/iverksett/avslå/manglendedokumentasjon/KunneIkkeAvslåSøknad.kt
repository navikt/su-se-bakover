package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon

import no.nav.su.se.bakover.domain.Sak

sealed interface KunneIkkeAvslåSøknad {
    data class KunneIkkeOppretteSøknadsbehandling(
        val underliggendeFeil: Sak.KunneIkkeOppretteSøknadsbehandling,
    ) : KunneIkkeAvslåSøknad

    data class KunneIkkeIverksetteSøknadsbehandling(
        val underliggendeFeil: no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling,
    ) : KunneIkkeAvslåSøknad
}
