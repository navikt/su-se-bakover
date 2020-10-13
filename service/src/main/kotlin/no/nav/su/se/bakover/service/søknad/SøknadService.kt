package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

interface SøknadService {
    fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad
    fun hentSøknad(søknadId: UUID): Either<SøknadServiceFeil.FantIkkeSøknad, Søknad>
    fun trekkSøknad(søknadId: UUID, saksbehandler: Saksbehandler): Either<SøknadServiceFeil, SøknadTrukketOk>
}

sealed class SøknadServiceFeil {
    object KunneIkkeTrekkeSøknad : SøknadServiceFeil()
    object SøknadErAlleredeTrukket : SøknadServiceFeil()
    object SøknadHarEnBehandling : SøknadServiceFeil()
    object FantIkkeSøknad : SøknadServiceFeil()
}

object SøknadTrukketOk
