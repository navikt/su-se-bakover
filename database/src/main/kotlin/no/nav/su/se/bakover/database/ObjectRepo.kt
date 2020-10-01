package no.nav.su.se.bakover.database

import no.nav.su.se.bakover.domain.Behandling
import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.oppdrag.Oppdrag
import java.util.UUID

interface ObjectRepo {
    fun opprettSak(fnr: Fnr): Sak
    fun hentBehandling(behandlingId: UUID): Behandling?
    fun hentOppdrag(sakId: UUID): Oppdrag
}
