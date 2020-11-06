package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import java.util.UUID

interface SøknadService {
    fun nySøknad(søknadInnhold: SøknadInnhold): Either<KunneIkkeOppretteSøknad, Søknad>
    fun hentSøknad(søknadId: UUID): Either<FantIkkeSøknad, Søknad>
    fun lagUtskrift(søknadId: UUID): Either<KunneIkkeLageSøknadsutskrift, ByteArray>
}

object FantIkkeSøknad

sealed class KunneIkkeOppretteSøknad {
    object FantIkkePerson : KunneIkkeOppretteSøknad()
}

sealed class KunneIkkeLageSøknadsutskrift {
    object FantIkkeSøknad : KunneIkkeLageSøknadsutskrift()
    object KunneIkkeLagePdf : KunneIkkeLageSøknadsutskrift()
}
