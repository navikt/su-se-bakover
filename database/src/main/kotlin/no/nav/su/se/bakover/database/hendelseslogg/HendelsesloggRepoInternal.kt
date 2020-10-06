package no.nav.su.se.bakover.database.hendelseslogg

import kotliquery.Row
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.HendelseListReader

internal object HendelsesloggRepoInternal {
    fun hentHendelseslogg(id: String, session: Session) =
        "select * from hendelseslogg where id=:id".hent(
            mapOf("id" to id),
            session
        ) { it.toHendelseslogg() }
}

internal fun Row.toHendelseslogg(): Hendelseslogg {
    return Hendelseslogg(
        id = string(columnLabel = "id"),
        hendelser = stringOrNull("hendelser")?.let { HendelseListReader.readValue(it) } ?: mutableListOf()
    )
}
