package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import java.util.UUID

interface SøknadService {
    fun nySøknad(søknadInnhold: SøknadInnhold): Either<KunneIkkeOppretteSøknad, Sak>
    fun hentSøknad(søknadId: UUID): Either<KunneIkkeLukkeSøknad.FantIkkeSøknad, Søknad>
    fun lukkSøknad(
        søknadId: UUID,
        lukketSøknad: Søknad.Lukket
    ): Either<KunneIkkeLukkeSøknad, Sak>

    fun lagLukketSøknadBrevutkast(
        søknadId: UUID,
        lukketSøknad: Søknad.Lukket
    ): Either<KunneIkkeLageBrevutkast, ByteArray>
}

sealed class KunneIkkeLukkeSøknad {
    object SøknadErAlleredeLukket : KunneIkkeLukkeSøknad()
    object SøknadHarEnBehandling : KunneIkkeLukkeSøknad()
    object KunneIkkeSendeBrev : KunneIkkeLukkeSøknad()
    object FantIkkeSøknad : KunneIkkeLukkeSøknad()
    object FantIkkePerson : KunneIkkeLukkeSøknad()
    object KunneIkkeJournalføreBrev : KunneIkkeLukkeSøknad()
}

sealed class KunneIkkeLageBrevutkast {
    object FantIkkeSøknad : KunneIkkeLageBrevutkast()
    object FeilVedHentingAvPerson : KunneIkkeLageBrevutkast()
    object FeilVedGenereringAvBrevutkast : KunneIkkeLageBrevutkast()
}

sealed class KunneIkkeOppretteSøknad {
    object FantIkkePerson : KunneIkkeOppretteSøknad()
}
