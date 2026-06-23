package no.nav.su.se.bakover.database.notat

import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.notat.NotatSaksbehandlerJson.Companion.toJson
import no.nav.su.se.bakover.domain.notat.Notat
import no.nav.su.se.bakover.domain.notat.NotatRepo
import java.util.UUID

class NotatRepoImpl(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : NotatRepo {

    override fun opprett(notat: Notat) {
        dbMetrics.timeQuery("opprettNotat") {
            sessionFactory.withSession { session ->
                """
                INSERT INTO notat (id, sakid, referanseid, notat, opprettet, endret, saksbehandler)
                VALUES (:id, :sakid, :referanseid, :notat, :opprettet, :endret, :saksbehandler::jsonb)
                """.trimIndent().insert(
                    mapOf(
                        "id" to notat.id,
                        "sakid" to notat.sakId,
                        "referanseid" to notat.referanseId,
                        "notat" to notat.notat,
                        "opprettet" to notat.opprettet,
                        "endret" to notat.endret,
                        "saksbehandler" to serialize(notat.saksbehandler.toJson()),
                    ),
                    session,
                )
            }
        }
    }

    override fun oppdater(notat: Notat) {
        dbMetrics.timeQuery("oppdaterNotat") {
            sessionFactory.withSession { session ->
                """
                UPDATE notat
                SET notat = :notat,
                    endret = :endret,
                    saksbehandler = :saksbehandler::jsonb
                WHERE id = :id
                """.trimIndent().oppdatering(
                    mapOf(
                        "id" to notat.id,
                        "notat" to notat.notat,
                        "endret" to notat.endret,
                        "saksbehandler" to serialize(notat.saksbehandler.toJson()),
                    ),
                    session,
                )
            }
        }
    }

    override fun hent(notatId: UUID): Notat? =
        dbMetrics.timeQuery("hentNotat") {
            sessionFactory.withSession { session ->
                """
                SELECT * FROM notat WHERE id = :id
                """.trimIndent().hent(
                    mapOf("id" to notatId),
                    session,
                ) { rowToNotat(it) }
            }
        }

    override fun hentForSak(sakId: UUID): List<Notat> =
        dbMetrics.timeQuery("hentNotaterForSak") {
            sessionFactory.withSession { session ->
                """
                SELECT * FROM notat WHERE sakid = :sakid ORDER BY opprettet
                """.trimIndent().hentListe(
                    mapOf("sakid" to sakId),
                    session,
                ) { rowToNotat(it) }
            }
        }

    private fun rowToNotat(row: Row): Notat = Notat(
        id = row.uuid("id"),
        sakId = row.uuid("sakid"),
        referanseId = row.uuid("referanseid"),
        notat = row.string("notat"),
        opprettet = row.tidspunkt("opprettet"),
        endret = row.tidspunkt("endret"),
        saksbehandler = deserialize<NotatSaksbehandlerJson>(row.string("saksbehandler")).toDomain(),
    )
}
