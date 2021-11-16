package no.nav.su.se.bakover.domain.klage

import no.nav.su.se.bakover.database.Session
import java.util.UUID

interface KlageRepo {
    fun opprett(klage: Klage)
    fun hentKlage(klageId: UUID): Klage?
    fun hentKlager(sakid: UUID, session: Session): List<Klage>
}
