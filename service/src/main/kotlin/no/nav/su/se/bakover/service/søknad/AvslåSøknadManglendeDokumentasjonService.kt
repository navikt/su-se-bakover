package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.service.søknadsbehandling.SøknadsbehandlingService
import java.util.UUID

interface AvslåSøknadManglendeDokumentasjonService {
    fun avslå(request: AvslåManglendeDokumentasjonRequest): Either<KunneIkkeAvslåSøknad, Sak>
}

sealed class KunneIkkeAvslåSøknad {
    data class KunneIkkeOppretteSøknadsbehandling(val feil: SøknadsbehandlingService.KunneIkkeOpprette) :
        KunneIkkeAvslåSøknad()

    object SøknadsbehandlingIUgyldigTilstandForAvslag : KunneIkkeAvslåSøknad()
    object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeAvslåSøknad()
    object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeAvslåSøknad()
    object KunneIkkeHentePerson : KunneIkkeAvslåSøknad()
    object KunneIkkeGenererePDF : KunneIkkeAvslåSøknad()
    object FantIkkeSak : KunneIkkeAvslåSøknad()
    object FantIkkeSøknad : KunneIkkeAvslåSøknad()
}

data class AvslåManglendeDokumentasjonRequest(
    val søknadId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekstTilBrev: String = "",
)
