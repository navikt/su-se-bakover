package no.nav.su.se.bakover.database.søknad

import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

interface SøknadRepo {
    fun hentSøknad(søknadId: UUID): Søknad?
    fun opprettSøknad(søknad: Søknad)
    fun lukkSøknad(søknadId: UUID, lukket: Søknad.Lukket)
    fun harSøknadPåbegyntBehandling(søknadId: UUID): Boolean
}
