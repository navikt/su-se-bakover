package no.nav.su.se.bakover.service.søknad

import arrow.core.Either
import no.nav.su.se.bakover.database.søknad.KunneIkkeTrekkeSøknad
import no.nav.su.se.bakover.database.søknad.SøknadTrukketOk
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.TrukketSøknadBody
import java.util.UUID

interface SøknadService {
    fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad
    fun hentSøknad(søknadId: UUID): Either<FantIkkeSøknad, Søknad>
    fun trekkSøknad(trukketSøknadBody: TrukketSøknadBody): Either<KunneIkkeTrekkeSøknad, SøknadTrukketOk>
}

object FantIkkeSøknad
