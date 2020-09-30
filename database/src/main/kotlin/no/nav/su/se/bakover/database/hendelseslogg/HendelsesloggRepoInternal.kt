package no.nav.su.se.bakover.database.hendelseslogg

import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent

internal object HendelsesloggRepoInternal {
    fun hentHendelseslogg(id: String, session: Session) =
        "select * from hendelseslogg where id=:id".hent(
            mapOf("id" to id),
            session
        ) { it.toHendelseslogg() }
}
