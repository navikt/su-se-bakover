package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.Either
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest

interface LukkSøknadService {
    fun lukkSøknad(request: LukkSøknadRequest): Either<KunneIkkeLukkeSøknad, Sak>
    fun lagBrevutkast(request: LukkSøknadRequest): Either<KunneIkkeLageBrevutkast, ByteArray>
}

sealed class KunneIkkeLukkeSøknad {
    object FantIkkeSøknad : KunneIkkeLukkeSøknad()
    object FantIkkePerson : KunneIkkeLukkeSøknad()
    object SøknadErAlleredeLukket : KunneIkkeLukkeSøknad()
    object SøknadHarEnBehandling : KunneIkkeLukkeSøknad()
    object UgyldigTrukketDato : KunneIkkeLukkeSøknad()
    object SøknadManglerOppgave : KunneIkkeLukkeSøknad()
    object KunneIkkeGenerereDokument : KunneIkkeLukkeSøknad()
}

sealed class KunneIkkeLageBrevutkast {
    object FantIkkeSøknad : KunneIkkeLageBrevutkast()
    object FantIkkePerson : KunneIkkeLageBrevutkast()
    object KunneIkkeLageBrev : KunneIkkeLageBrevutkast()
    object UkjentBrevtype : KunneIkkeLageBrevutkast()
}
