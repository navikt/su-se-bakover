package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.domain.Sak
<<<<<<< HEAD

=======
>>>>>>> forbedringer av trekking av søknad
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

interface SøknadService {
    fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad
    fun hentSøknad(søknadId: UUID): Either<KunneIkkeLukkeSøknad.FantIkkeSøknad, Søknad>
    fun trekkSøknad(søknadId: UUID, saksbehandler: Saksbehandler, begrunnelse: String): Either<KunneIkkeLukkeSøknad, Sak>
}

sealed class KunneIkkeLukkeSøknad {
    object SøknadErAlleredeLukket : KunneIkkeLukkeSøknad()
    object SøknadHarEnBehandling : KunneIkkeLukkeSøknad()
    object KunneIkkeSendeBrev : KunneIkkeLukkeSøknad()
    object FantIkkeSøknad : KunneIkkeLukkeSøknad()
}
