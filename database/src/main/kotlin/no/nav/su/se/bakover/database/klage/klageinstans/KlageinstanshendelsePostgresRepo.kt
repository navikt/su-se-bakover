package no.nav.su.se.bakover.database.klage.klageinstans

import arrow.core.Either
import kotliquery.Row
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.domain.journal.JournalpostId
import no.nav.su.se.bakover.domain.klage.KlageinstanshendelseRepo
import no.nav.su.se.bakover.domain.klage.ProsessertKlageinstanshendelse
import no.nav.su.se.bakover.domain.klage.UprosessertKlageinstanshendelse
import no.nav.su.se.bakover.domain.oppgave.OppgaveId
import org.postgresql.util.PSQLException
import java.util.UUID

internal class KlageinstanshendelsePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
) : KlageinstanshendelseRepo {

    internal enum class KlageinstanshendelseType(private val verdi: String) {
        UPROSESSERT("UPROSESSERT"),
        PROSESSERT("PROSESSERT"),
        FEIL("FEIL");

        override fun toString(): String = verdi
    }

    override fun lagre(hendelse: UprosessertKlageinstanshendelse) {
        Either.catch {
            return sessionFactory.withSession { session ->
                """
                insert into klageinstanshendelse(id, opprettet, type, metadata)
                values(:id, :opprettet, :type, to_jsonb(:metadata::jsonb))
                """.trimIndent()
                    .insert(
                        params = mapOf(
                            "id" to hendelse.id,
                            "opprettet" to hendelse.opprettet,
                            "type" to KlageinstanshendelseType.UPROSESSERT.toString(),
                            "metadata" to hendelse.metadata.toDatabaseJson(),
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

    override fun lagre(hendelse: ProsessertKlageinstanshendelse, transactionContext: TransactionContext) {
        transactionContext.withTransaction { transaction ->
            """
                update klageinstanshendelse
                    set type = :type,
                    oppgaveId = :oppgaveid,
                    utlest_utfall = :utlest_utfall,
                    utlest_journalpostid = :utlest_journalpostid,
                    utlest_klageid = :utlest_klageid
                    where id = :id
            """.trimIndent()
                .insert(
                    params = mapOf(
                        "id" to hendelse.id,
                        "type" to KlageinstanshendelseType.PROSESSERT.toString(),
                        "oppgaveid" to hendelse.oppgaveId,
                        "utlest_utfall" to hendelse.utfall.toDatabaseType(),
                        "utlest_journalpostid" to hendelse.journalpostIDer,
                        "utlest_klageid" to hendelse.klageId,
                    ),
                    session = transaction,
                )
        }
    }

    override fun hentUbehandlaKlageinstanshendelser(): List<UprosessertKlageinstanshendelse> {
        return sessionFactory.withSession { session ->
            """
                select * from klageinstanshendelse where type = '${KlageinstanshendelseType.UPROSESSERT}'
            """.trimIndent().hentListe(
                emptyMap(),
                session,
            ) { rowToKlage(it) }
        }
    }

    override fun markerSomFeil(id: UUID) {
        sessionFactory.withSession { session ->
            """
            update klageinstanshendelse set type = '${KlageinstanshendelseType.FEIL}' where id = :id
            """.trimIndent().oppdatering(mapOf("id" to id), session)
        }
    }

    private fun rowToKlage(row: Row): UprosessertKlageinstanshendelse {
        return UprosessertKlageinstanshendelse(
            id = row.uuid("id"),
            opprettet = row.tidspunkt("opprettet"),
            metadata = KlageinstanshendelseMetadataJson.toKlageinstanshendelseMetadata(row.string("metadata")),
        )
    }

    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
    }

    internal fun hentProsesserteKlageinstanshendelser(klageId: UUID): List<ProsessertKlageinstanshendelse> {
        return sessionFactory.withSession { session ->
            """
            select * from klageinstanshendelse where utlest_klageid = :klageid AND type = :type
            """.trimIndent().hentListe(
                mapOf(
                    "klageid" to klageId,
                    "type" to KlageinstanshendelseType.PROSESSERT.toString(),
                ),
                session,
            ) { row ->
                ProsessertKlageinstanshendelse(
                    id = row.uuid("id"),
                    opprettet = row.tidspunkt("opprettet"),
                    klageId = row.uuid("utlest_klageid"),
                    utfall = UtfallJson.valueOf(row.string("utlest_utfall")).toDomain(),
                    journalpostIDer = row.array<String>("utlest_journalpostid").map { JournalpostId(it) },
                    oppgaveId = OppgaveId(row.string("oppgaveid")),
                )
            }
        }
    }
}
