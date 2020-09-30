package no.nav.su.se.bakover.database.hendelseslogg

import kotliquery.using
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.sessionOf
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.HendelseListWriter
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

    override fun oppdaterHendelseslogg(hendelseslogg: Hendelseslogg): Hendelseslogg {
        "insert into hendelseslogg (id, hendelser) values (:id, to_json(:hendelser::json)) on conflict(id) do update set hendelser=to_json(:hendelser::json)".oppdatering(
            mapOf(
                "id" to hendelseslogg.id,
                "hendelser" to HendelseListWriter.writeValueAsString(hendelseslogg.hendelser())
            )
        )
        return hendelseslogg
    }

    private fun String.oppdatering(params: Map<String, Any?>) {
        using(sessionOf(dataSource)) {
            this.oppdatering(params, it)
        }
    }
}
