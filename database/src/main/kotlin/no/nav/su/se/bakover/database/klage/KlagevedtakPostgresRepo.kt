package no.nav.su.se.bakover.database.klage

import arrow.core.Either
import kotliquery.Row
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.domain.klage.KlagevedtakRepo
import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlagevedtak
import org.postgresql.util.PSQLException

internal class KlagevedtakPostgresRepo(private val sessionFactory: PostgresSessionFactory) : KlagevedtakRepo {

    override fun lagre(klagevedtak: UprosessertFattetKlagevedtak) {
        Either.catch {
            return sessionFactory.withSession { session ->
                """
                insert into klagevedtak(id, opprettet, type, metadata)
                values(:id, :opprettet, :type, to_jsonb(:metadata::jsonb))
                """.trimIndent()
                    .insert(
                        params = mapOf(
                            "id" to klagevedtak.id,
                            "opprettet" to klagevedtak.opprettet,
                            "type" to "UPROSESSERT",
                            "metadata" to klagevedtak.metadata.toDatabaseJson(),
                        ),
                        session = session,
                    )
            }
        }.tapLeft {
            if (it is PSQLException && it.message!!.contains("duplicate key value violates unique constraint \"klagevedtak_metadata_hendelseid_idx\"")) {
                // Swallowing this duplicate (part of the dedup routine)
                // Can't use expression indexes as constraints, so we can't use on conflict ... do nothing: https://stackoverflow.com/questions/16236365/postgresql-conditionally-unique-constraint
            } else {
                throw it
            }
        }
    }

    override fun hentUbehandlaKlagevedtak(): List<UprosessertFattetKlagevedtak> {
        return sessionFactory.withSession { session ->
            """
                select * from klagevedtak where type = 'UPROSESSERT'
            """.trimIndent().hentListe(
                emptyMap(),
                session,
            ) { rowToKlage(it) }
        }
    }

    private fun rowToKlage(row: Row): UprosessertFattetKlagevedtak {
        return UprosessertFattetKlagevedtak(
            id = row.uuid("id"),
            opprettet = row.tidspunkt("opprettet"),
            metadata = KlagevedtakMetadataJson.toKlagevedtakMetadata(row.string("metadata")),
        )
    }
}
