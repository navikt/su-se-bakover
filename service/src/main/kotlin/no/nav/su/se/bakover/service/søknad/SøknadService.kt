package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.domain.NavIdentBruker.Saksbehandler
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import java.time.LocalDate
import java.util.UUID

interface SøknadService {
    fun nySøknad(søknadInnhold: SøknadInnhold): Either<KunneIkkeOppretteSøknad, Sak>
    fun hentSøknad(søknadId: UUID): Either<KunneIkkeLukkeSøknad.FantIkkeSøknad, Søknad>
    fun trekkSøknad(søknadId: UUID, saksbehandler: Saksbehandler, begrunnelse: String): Either<KunneIkkeLukkeSøknad, Sak>
    fun lagBrevutkastForTrukketSøknad(søknadId: UUID, trukketDato: LocalDate): Either<KunneIkkeLageBrevutkast, ByteArray>
}

sealed class KunneIkkeLukkeSøknad {
    object SøknadErAlleredeLukket : KunneIkkeLukkeSøknad()
    object SøknadHarEnBehandling : KunneIkkeLukkeSøknad()
    object FantIkkeSøknad : KunneIkkeLukkeSøknad()
}

sealed class KunneIkkeLageBrevutkast {
    object FantIkkeSøknad : KunneIkkeLageBrevutkast()
    object KunneIkkeLageBrev : KunneIkkeLageBrevutkast()
}

sealed class KunneIkkeOppretteSøknad {
    object FantIkkePerson : KunneIkkeOppretteSøknad()
}
