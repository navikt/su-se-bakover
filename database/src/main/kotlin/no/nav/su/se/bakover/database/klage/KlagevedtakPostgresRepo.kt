package no.nav.su.se.bakover.database.klage

import arrow.core.Either
import kotliquery.Row
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.domain.klage.KlagevedtakRepo
import no.nav.su.se.bakover.domain.klage.ProsessertKlageinstansvedtak
import no.nav.su.se.bakover.domain.klage.UprosessertFattetKlageinstansvedtak
import org.postgresql.util.PSQLException
import java.util.UUID

internal class KlagevedtakPostgresRepo(private val sessionFactory: PostgresSessionFactory) : KlagevedtakRepo {
    internal enum class KlagevedtakType(private val verdi: String) {
        UPROSESSERT("UPROSESSERT"),
        PROSESSERT("PROSESSERT"),
        FEIL("FEIL");

        override fun toString(): String = verdi
    }

    override fun lagre(klagevedtak: UprosessertFattetKlageinstansvedtak) {
        Either.catch {
            return sessionFactory.withSession { session ->
                """
                insert into klageinstansvedtak(id, opprettet, type, metadata)
                values(:id, :opprettet, :type, to_jsonb(:metadata::jsonb))
                """.trimIndent()
                    .insert(
                        params = mapOf(
                            "id" to klagevedtak.id,
                            "opprettet" to klagevedtak.opprettet,
                            "type" to KlagevedtakType.UPROSESSERT.toString(),
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

    override fun lagre(klagevedtak: ProsessertKlageinstansvedtak, transactionContext: TransactionContext) {
        transactionContext.withTransaction { transaction ->
            """
                update klageinstansvedtak
                    set type = :type,
                    oppgaveId = :oppgaveid,
                    utlest_utfall = :utlest_utfall,
                    utlest_journalpostid = :utlest_journalpostid,
                    utlest_klageid = :utlest_klageid
                    where id = :id
            """.trimIndent()
                .insert(
                    params = mapOf(
                        "id" to klagevedtak.id,
                        "type" to KlagevedtakType.PROSESSERT.toString(),
                        "oppgaveid" to klagevedtak.oppgaveId,
                        "utlest_utfall" to klagevedtak.utfall.toDatabaseType(),
                        "utlest_journalpostid" to klagevedtak.vedtaksbrevReferanse,
                        "utlest_klageid" to klagevedtak.klageId,
                    ),
                    session = transaction,
                )
        }
    }

    override fun hentUbehandlaKlagevedtak(): List<UprosessertFattetKlageinstansvedtak> {
        return sessionFactory.withSession { session ->
            """
                select * from klageinstansvedtak where type = '${KlagevedtakType.UPROSESSERT}'
            """.trimIndent().hentListe(
                emptyMap(),
                session,
            ) { rowToKlage(it) }
        }
    }

    override fun markerSomFeil(id: UUID) {
        sessionFactory.withSession { session ->
            """
            update klageinstansvedtak set type = '${KlagevedtakType.FEIL}' where id = :id
            """.trimIndent().oppdatering(mapOf("id" to id), session)
        }
    }

    private fun rowToKlage(row: Row): UprosessertFattetKlageinstansvedtak {
        return UprosessertFattetKlageinstansvedtak(
            id = row.uuid("id"),
            opprettet = row.tidspunkt("opprettet"),
            metadata = KlagevedtakMetadataJson.toKlagevedtakMetadata(row.string("metadata")),
        )
    }

    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
    }
}
