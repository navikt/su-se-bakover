package no.nav.su.se.bakover.database.hendelseslogg

import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.hendelseslogg.Hendelseslogg
import no.nav.su.se.bakover.domain.hendelseslogg.hendelse.HendelseListWriter
import javax.sql.DataSource

internal class HendelsesloggPostgresRepo(
    private val dataSource: DataSource
) : HendelsesloggRepo {
    override fun hentHendelseslogg(id: String) =
        dataSource.withSession { session ->
            "select * from hendelseslogg where id=:id".hent(
                mapOf("id" to id),
                session
            ) { it.toHendelseslogg() }
        }

    override fun oppdaterHendelseslogg(hendelseslogg: Hendelseslogg): Hendelseslogg {
        dataSource.withSession { session ->
            "insert into hendelseslogg (id, hendelser) values (:id, to_json(:hendelser::json)) on conflict(id) do update set hendelser=to_json(:hendelser::json)".oppdatering(
                mapOf(
                    "id" to hendelseslogg.id,
                    "hendelser" to HendelseListWriter.writeValueAsString(hendelseslogg.hendelser())
                ),
                session
            )
        }
        return hendelseslogg
    }
}
