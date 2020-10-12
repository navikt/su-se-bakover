package no.nav.su.se.bakover.database.søknad

import no.nav.su.se.bakover.domain.Saksbehandler
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

interface SøknadRepo {
    fun hentSøknad(søknadId: UUID): Søknad?
    fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad
    fun trekkSøknad(søknadId: UUID, saksbehandler: Saksbehandler)
}
