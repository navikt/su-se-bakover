package no.nav.su.se.bakover.domain.klage

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface KlageRepo {
    fun lagre(klage: Klage)
    fun hentKlage(klageId: UUID): Klage?
    fun hentKlager(sakid: UUID, sessionContext: SessionContext = defaultSessionContext()): List<Klage>
    fun defaultSessionContext(): SessionContext
}
