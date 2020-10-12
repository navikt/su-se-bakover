package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

interface SøknadService {
    fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad
    fun hentSøknad(søknadId: UUID): Either<FantIkkeSøknad, Søknad>
    fun trekkSøknad(søknadId: UUID, saksbehandler: Saksbehandler): Either<TrekkSøknadFeil, SøknadTrukketOk>
}

sealed class TrekkSøknadFeil {
    object KunneIkkeTrekkeSøknad : TrekkSøknadFeil()
    object SøknadErAlleredeTrukket : TrekkSøknadFeil()
    object SøknadHarEnBehandling : TrekkSøknadFeil()
}

object SøknadTrukketOk
object FantIkkeSøknad
