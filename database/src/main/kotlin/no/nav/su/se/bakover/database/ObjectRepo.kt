package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Søknad
import no.nav.su.se.bakover.domain.beregning.Beregning
import java.util.UUID

interface ObjectRepo {
    fun hentSak(fnr: Fnr): Sak?
    fun hentSak(sakId: UUID): Sak?
    fun opprettSak(fnr: Fnr): Sak
    fun hentSøknad(søknadId: UUID): Søknad?
    fun hentBehandling(behandlingId: UUID): Behandling?
    fun hentBeregning(behandlingId: UUID): Beregning?
}
