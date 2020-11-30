package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.SøknadInnhold
import java.util.UUID

interface SøknadService {
    fun nySøknad(søknadInnhold: SøknadInnhold): Either<KunneIkkeOppretteSøknad, Pair<Saksnummer, Søknad>>
    fun hentSøknad(søknadId: UUID): Either<FantIkkeSøknad, Søknad>
    fun hentSøknadPdf(søknadId: UUID): Either<KunneIkkeLageSøknadPdf, ByteArray>
}

object FantIkkeSøknad

sealed class KunneIkkeOppretteSøknad {
    object FantIkkePerson : KunneIkkeOppretteSøknad()
}

sealed class KunneIkkeLageSøknadPdf {
    object FantIkkeSøknad : KunneIkkeLageSøknadPdf()
    object KunneIkkeLagePdf : KunneIkkeLageSøknadPdf()
    object FantIkkePerson : KunneIkkeLageSøknadPdf()
    object FantIkkeSak : KunneIkkeLageSøknadPdf()
}
