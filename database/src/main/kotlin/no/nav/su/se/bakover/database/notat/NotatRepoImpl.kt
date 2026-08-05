package no.nav.su.se.bakover.database.notat

import kotliquery.Row
import no.nav.su.se.bakover.common.deserializeList
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.notat.NotatHendelserJson.Companion.toJson
import no.nav.su.se.bakover.domain.notat.Notat
import no.nav.su.se.bakover.domain.notat.NotatRepo
import no.nav.su.se.bakover.domain.notat.ReferanseType
import java.util.UUID

class NotatRepoImpl(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : NotatRepo {

    override fun opprett(notat: Notat) {
        dbMetrics.timeQuery("opprettNotat") {
            sessionFactory.withSession { session ->
                """
                INSERT INTO notat (id, sakid, referanseid, referanse_type, notat, attestant_notat, opprettet, endret, hendelser)
                VALUES (:id, :sakid, :referanseid, :referanse_type, :notat, :attestant_notat, :opprettet, :endret, :hendelser::jsonb)
                """.trimIndent().insert(
                    mapOf(
                        "id" to notat.id,
                        "sakid" to notat.sakId,
                        "referanseid" to notat.referanseId,
                        "referanse_type" to notat.referanseType.name,
                        "notat" to notat.notat,
                        "attestant_notat" to notat.attestantNotat,
                        "opprettet" to notat.opprettet,
                        "endret" to notat.endret,
                        "hendelser" to serialize(notat.hendelser.toJson()),
                    ),
                    session,
                )
            }
        }
    }

    override fun oppdaterNotatSaksbehandler(notat: Notat) {
        dbMetrics.timeQuery("oppdaterNotatSaksbehandler") {
            sessionFactory.withSession { session ->
                """
                UPDATE notat
                SET notat = :notat,
                    endret = :endret,
                    hendelser = :hendelser::jsonb
                WHERE id = :id
                """.trimIndent().oppdatering(
                    mapOf(
                        "id" to notat.id,
                        "notat" to notat.notat,
                        "endret" to notat.endret,
                        "hendelser" to serialize(notat.hendelser.toJson()),
                    ),
                    session,
                )
            }
        }
    }

    override fun oppdaterAttestantNotat(notat: Notat) {
        dbMetrics.timeQuery("oppdaterAttestantNotat") {
            sessionFactory.withSession { session ->
                """
                UPDATE notat
                SET attestant_notat = :attestant_notat,
                    endret = :endret,
                    hendelser = :hendelser::jsonb
                WHERE id = :id
                """.trimIndent().oppdatering(
                    mapOf(
                        "id" to notat.id,
                        "attestant_notat" to notat.attestantNotat,
                        "endret" to notat.endret,
                        "hendelser" to serialize(notat.hendelser.toJson()),
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

    override fun hentForReferanse(referanseId: UUID, referanseType: ReferanseType): Notat? =
        dbMetrics.timeQuery("hentNotaterForReferanse") {
            sessionFactory.withSession { session ->
                """
                    SELECT * FROM notat WHERE referanseid = :referanseid AND referanse_type = :referanse_type ORDER BY opprettet
                """.trimIndent().hent(
                    mapOf(
                        "referanseid" to referanseId,
                        "referanse_type" to referanseType.name,
                    ),
                    session,
                ) { rowToNotat(it) }
            }
        }

    override fun eksistererForReferanse(sakId: UUID, referanseId: UUID): Boolean =
        dbMetrics.timeQuery("eksistererNotatForReferanse") {
            sessionFactory.withSession { session ->
                """
                SELECT EXISTS(SELECT 1 FROM notat WHERE sakid = :sakid AND referanseid = :referanseid)
                """.trimIndent().hent(
                    mapOf("sakid" to sakId, "referanseid" to referanseId),
                    session,
                ) { it.boolean("exists") } ?: false
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
        referanseType = ReferanseType.valueOf(row.string("referanse_type")),
        notat = row.string("notat"),
        opprettet = row.tidspunkt("opprettet"),
        endret = row.tidspunkt("endret"),
        attestantNotat = row.stringOrNull("attestant_notat") ?: "",
        hendelser = deserializeList<NotatHendelserJson>(row.string("hendelser")).map { it.toDomain() },
    )
}
