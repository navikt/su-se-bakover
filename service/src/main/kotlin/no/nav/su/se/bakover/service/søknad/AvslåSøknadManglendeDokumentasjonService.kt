package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument
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
    data class KunneIkkeLageDokument(val nested: no.nav.su.se.bakover.service.brev.KunneIkkeLageDokument) : KunneIkkeAvslåSøknad()
    object FantIkkeSak : KunneIkkeAvslåSøknad()
}

data class AvslåManglendeDokumentasjonRequest(
    val søknadId: UUID,
    val saksbehandler: NavIdentBruker.Saksbehandler,
    val fritekstTilBrev: String = "",
)
