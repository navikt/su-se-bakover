package no.nav.su.se.bakover.database.oppdrag

import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import java.util.UUID

internal object OppdragRepoInternal {
    fun hentOppdragForSak(sakId: UUID, session: Session) =
        "select * from oppdrag where sakId=:sakId".hent(mapOf("sakId" to sakId), session) {
            it.toOppdrag(session)
        }
}
