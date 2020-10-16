package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

interface SøknadService {
    fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad
    fun hentSøknad(søknadId: UUID): Either<KunneIkkeLukkeSøknad.FantIkkeSøknad, Søknad>
    fun lukkSøknad(søknadId: UUID, saksbehandler: Saksbehandler): Either<KunneIkkeLukkeSøknad, Sak>
    fun lukketBrevutkast(søknadId: UUID, typeLukking: Søknad.TypeLukking): Either<KunneIkkeLageBrevutkast, ByteArray>
}

sealed class KunneIkkeLukkeSøknad {
    object SøknadErAlleredeLukket : KunneIkkeLukkeSøknad()
    object SøknadHarEnBehandling : KunneIkkeLukkeSøknad()
    object KunneIkkeSendeBrev : KunneIkkeLukkeSøknad()
    object FantIkkeSøknad : KunneIkkeLukkeSøknad()
}

sealed class KunneIkkeLageBrevutkast {
    object FantIkkeSøknad : KunneIkkeLageBrevutkast()
    object FeilVedHentingAvPerson : KunneIkkeLageBrevutkast()
}
