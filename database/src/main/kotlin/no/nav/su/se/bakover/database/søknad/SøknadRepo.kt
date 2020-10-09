package no.nav.su.se.bakover.database.søknad

import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.TrukketSøknadBody
import java.util.UUID

interface SøknadRepo {
    fun hentSøknad(søknadId: UUID): Søknad?
    fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad
    fun trekkSøknad(trukketSøknadBody: TrukketSøknadBody)
}

object SøknadTrukketOk
object KunneIkkeTrekkeSøknad
