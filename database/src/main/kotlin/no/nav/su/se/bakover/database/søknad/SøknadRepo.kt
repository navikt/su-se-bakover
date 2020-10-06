package no.nav.su.se.bakover.database.søknad

import no.nav.su.se.bakover.domain.AvsluttetBegrunnelse
import no.nav.su.se.bakover.domain.Søknad
import java.util.UUID

interface SøknadRepo {
    fun hentSøknad(søknadId: UUID): Søknad?
    fun opprettSøknad(sakId: UUID, søknad: Søknad): Søknad
    fun slettBehandlingForSøknad(søknadId: UUID, avsluttetBegrunnelse: AvsluttetBegrunnelse)
}
