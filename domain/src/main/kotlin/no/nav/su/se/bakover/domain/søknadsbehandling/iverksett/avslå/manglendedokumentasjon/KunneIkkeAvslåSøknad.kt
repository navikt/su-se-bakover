package no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.avslå.manglendedokumentasjon

import no.nav.su.se.bakover.domain.søknadsbehandling.tilAttestering.KunneIkkeSendeSøknadsbehandlingTilAttestering
import vilkår.opplysningsplikt.domain.KunneIkkeLageOpplysningspliktVilkår

sealed interface KunneIkkeAvslåSøknad {
    data class KunneIkkeOppretteSøknadsbehandling(
        val underliggendeFeil: behandling.søknadsbehandling.domain.KunneIkkeOppretteSøknadsbehandling,
    ) : KunneIkkeAvslåSøknad

    data class KunneIkkeIverksetteSøknadsbehandling(
        val underliggendeFeil: no.nav.su.se.bakover.domain.søknadsbehandling.iverksett.KunneIkkeIverksetteSøknadsbehandling,
    ) : KunneIkkeAvslåSøknad

    data class Attesteringsfeil(val feil: KunneIkkeSendeSøknadsbehandlingTilAttestering) : KunneIkkeAvslåSøknad

    data class Periodefeil(val underliggende: KunneIkkeLageOpplysningspliktVilkår) : KunneIkkeAvslåSøknad
}
