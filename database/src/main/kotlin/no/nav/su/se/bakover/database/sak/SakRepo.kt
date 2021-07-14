package no.nav.su.se.bakover.database.sak

import no.nav.su.se.bakover.domain.Fnr
import no.nav.su.se.bakover.domain.NySak
import no.nav.su.se.bakover.domain.Sak
import no.nav.su.se.bakover.domain.Saksnummer
import no.nav.su.se.bakover.domain.behandling.Restans
import java.util.UUID

interface SakRepo {
    fun hentSak(sakId: UUID): Sak?
    fun hentSak(fnr: Fnr): Sak?
    fun hentSak(saksnummer: Saksnummer): Sak?
    fun opprettSak(sak: NySak)
    fun hentRestanser(): List<Restans>
}
