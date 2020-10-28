package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import no.nav.su.se.bakover.service.søknad.lukk.KunneIkkeLukkeSøknad
import java.util.UUID

interface SøknadService {
    fun nySøknad(søknadInnhold: SøknadInnhold): Either<KunneIkkeOppretteSøknad, Sak>
    fun hentSøknad(søknadId: UUID): Either<KunneIkkeLukkeSøknad.FantIkkeSøknad, Søknad>
}

sealed class KunneIkkeOppretteSøknad {
    object FantIkkePerson : KunneIkkeOppretteSøknad()
}
