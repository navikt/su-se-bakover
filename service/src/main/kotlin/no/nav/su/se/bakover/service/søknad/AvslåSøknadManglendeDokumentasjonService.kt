package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.domain.bruker.NavIdentBruker
import no.nav.su.se.bakover.domain.sak.Sak
import java.util.UUID

interface AvslåSøknadManglendeDokumentasjonService {
    fun avslå(request: AvslåManglendeDokumentasjonRequest): Either<KunneIkkeAvslåSøknad, Sak>
}

sealed class KunneIkkeAvslåSøknad {
    sealed class KunneIkkeOppretteSøknadsbehandling : KunneIkkeAvslåSøknad() {
        object HarAlleredeÅpenSøknadsbehandling : KunneIkkeOppretteSøknadsbehandling()
        object FantIkkeSøknad : KunneIkkeOppretteSøknadsbehandling()
        object SøknadErLukket : KunneIkkeOppretteSøknadsbehandling()
        object SøknadHarAlleredeBehandling : KunneIkkeOppretteSøknadsbehandling()
        object SøknadManglerOppgave : KunneIkkeOppretteSøknadsbehandling()
    }

    object SøknadsbehandlingIUgyldigTilstandForAvslag : KunneIkkeAvslåSøknad()
    object KunneIkkeFinneGjeldendeUtbetaling : KunneIkkeAvslåSøknad()
    object KunneIkkeHenteNavnForSaksbehandlerEllerAttestant : KunneIkkeAvslåSøknad()
    object KunneIkkeHentePerson : KunneIkkeAvslåSøknad()
    object KunneIkkeGenererePDF : KunneIkkeAvslåSøknad()
    object FantIkkeSak : KunneIkkeAvslåSøknad()
}

data class AvslåManglendeDokumentasjonRequest(
    val søknadId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekstTilBrev: String = "",
)
