package no.nav.su.se.bakover.database.hendelseslogg

import kotliquery.using
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.sessionOf
import javax.sql.DataSource

internal class HendelsesloggPostgresRepo(
    private val dataSource: DataSource
) : HendelsesloggRepo {
    override fun hentHendelseslogg(id: String) = using(sessionOf(dataSource)) { session ->
        "select * from hendelseslogg where id=:id".hent(
            mapOf("id" to id),
            session
        ) { it.toHendelseslogg() }
    }
}
