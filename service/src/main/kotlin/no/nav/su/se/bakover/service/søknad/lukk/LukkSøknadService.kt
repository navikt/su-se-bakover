package no.nav.su.se.bakover.service.søknad.lukk

import arrow.core.Either
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.søknad.LukkSøknadRequest

interface LukkSøknadService {
    fun lukkSøknad(request: LukkSøknadRequest): Either<KunneIkkeLukkeSøknad, LukketSøknad>
    fun lagBrevutkast(request: LukkSøknadRequest): Either<KunneIkkeLageBrevutkast, ByteArray>
}

sealed class KunneIkkeLukkeSøknad {
    object FantIkkeSøknad : KunneIkkeLukkeSøknad()
    object FantIkkePerson : KunneIkkeLukkeSøknad()
    object SøknadErAlleredeLukket : KunneIkkeLukkeSøknad()
    object SøknadHarEnBehandling : KunneIkkeLukkeSøknad()
    object UgyldigTrukketDato : KunneIkkeLukkeSøknad()
    object KunneIkkeJournalføreBrev : KunneIkkeLukkeSøknad()
    object SøknadManglerOppgave : KunneIkkeLukkeSøknad()
}

sealed class KunneIkkeLageBrevutkast {
    object FantIkkeSøknad : KunneIkkeLageBrevutkast()
    object FantIkkePerson : KunneIkkeLageBrevutkast()
    object KunneIkkeLageBrev : KunneIkkeLageBrevutkast()
    object UkjentBrevtype : KunneIkkeLageBrevutkast()
}

sealed class LukketSøknad {
    abstract val sak: Sak
    abstract val søknad: Søknad.Lukket

    data class UtenMangler(override val sak: Sak, override val søknad: Søknad.Lukket) : LukketSøknad()

    sealed class MedMangler : LukketSøknad() {
        data class KunneIkkeDistribuereBrev(override val sak: Sak, override val søknad: Søknad.Lukket) : MedMangler()
        data class KunneIkkeLukkeOppgave(override val sak: Sak, override val søknad: Søknad.Lukket) : MedMangler()
    }
}
