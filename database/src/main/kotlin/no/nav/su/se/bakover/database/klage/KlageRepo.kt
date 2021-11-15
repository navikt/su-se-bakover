package no.nav.su.se.bakover.database.klage

import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.domain.klage.Klage
import java.util.UUID

interface KlageRepo {
    fun opprett(klage: Klage)
    fun hentKlager(sakid: UUID, session: Session): List<Klage>
}
