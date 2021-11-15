package no.nav.su.se.bakover.database.klage

import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.Klage
import java.util.UUID
import javax.sql.DataSource

class KlagePostgresRepo(private val dataSource: DataSource) : KlageRepo {
    override fun opprett(klage: Klage) {
        dataSource.withSession { session ->
            """
                insert into klage(id, sakid, opprettet, journalpostid, saksbehandler)
                values(:id, :sakid, :opprettet, :journalpostid, :saksbehandler)
            """.trimIndent()
                .insert(
                    mapOf(
                        "id" to klage.id,
                        "sakid" to klage.sakId,
                        "opprettet" to klage.opprettet,
                        "journalpostid" to klage.journalpostId,
                        "saksbehandler" to klage.saksbehandler,
                    ),
                    session,
                )
        }
    }

    override fun hentKlager(sakid: UUID): List<Klage> =
        dataSource.withSession { session ->
            """
                select * from klage where sakid=:sakid
            """.trimIndent().hentListe(
                mapOf(
                    "sakid" to sakid,
                ),
                session,
            ) { row ->
                Klage(
                    id = row.uuid("id"),
                    opprettet = row.tidspunkt("opprettet"),
                    sakId = row.uuid("sakid"),
                    journalpostId = JournalpostId(row.string("journalpostid")),
                    saksbehandler = NavIdentBruker.Saksbehandler(row.string("saksbehandler")),
                )
            }
        }
}
