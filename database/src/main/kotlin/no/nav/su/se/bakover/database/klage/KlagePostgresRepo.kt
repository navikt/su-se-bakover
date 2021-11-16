package no.nav.su.se.bakover.database.klage

import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.klage.KlagePostgresRepo.Typer.Companion.databasetype
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.NavIdentBruker
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.Klage
import no.nav.su.se.bakover.domain.klage.OpprettetKlage
import java.util.UUID
import javax.sql.DataSource

class KlagePostgresRepo(private val dataSource: DataSource) : KlageRepo {
    override fun opprett(klage: Klage) {
        dataSource.withSession { session ->
            """
                insert into klage(id, sakid, opprettet, journalpostid, saksbehandler, type)
                values(:id, :sakid, :opprettet, :journalpostid, :saksbehandler, :type)
            """.trimIndent()
                .insert(
                    mapOf(
                        "id" to klage.id,
                        "sakid" to klage.sakId,
                        "opprettet" to klage.opprettet,
                        "journalpostid" to klage.journalpostId,
                        "saksbehandler" to klage.saksbehandler,
                        "type" to klage.databasetype(),
                    ),
                    session,
                )
        }
    }

    override fun hentKlager(sakid: UUID, session: Session): List<Klage> =
        """
                select * from klage where sakid=:sakid
        """.trimIndent().hentListe(
            mapOf(
                "sakid" to sakid,
            ),
            session,
        ) { row ->
            when (Typer.fromString(row.string("type"))) {
                Typer.OPPRETTET -> OpprettetKlage.create(
                    id = row.uuid("id"),
                    opprettet = row.tidspunkt("opprettet"),
                    sakId = row.uuid("sakid"),
                    journalpostId = JournalpostId(row.string("journalpostid")),
                    saksbehandler = NavIdentBruker.Saksbehandler(row.string("saksbehandler")),
                )
            }
        }

    private enum class Typer(val verdi: String) {
        OPPRETTET("opprettet");

        companion object {
            fun Klage.databasetype(): String {
                return when (this) {
                    is OpprettetKlage -> OPPRETTET
                }.toString()
            }

            fun fromString(value: String): Typer {
                return values().find { it.verdi == value }
                    ?: throw IllegalStateException("Ukjent typeverdi i klage-tabellen: $value")
            }
        }

        override fun toString() = verdi
    }
}
