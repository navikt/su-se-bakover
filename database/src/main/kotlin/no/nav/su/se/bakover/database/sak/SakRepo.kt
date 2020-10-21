package no.nav.su.se.bakover.database.sak

import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.Sak
import java.util.UUID

interface SakRepo {
    fun hentSak(sakId: UUID): Sak?
    fun hentSak(fnr: Fnr): Sak?
    fun opprettSak(sak: Sak)
}
