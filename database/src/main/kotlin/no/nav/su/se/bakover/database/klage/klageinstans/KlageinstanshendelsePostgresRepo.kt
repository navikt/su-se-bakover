package no.nav.su.se.bakover.database.klage.klageinstans

import arrow.core.Either
import behandling.klage.domain.KlageId
import behandling.klage.domain.UprosessertKlageinstanshendelse
import kotliquery.Row
import no.nav.su.se.bakover.common.domain.oppgave.OppgaveId
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunktOrNull
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.domain.klage.KlageinstanshendelseRepo
import no.nav.su.se.bakover.domain.klage.ProsessertKlageinstanshendelse
import org.postgresql.util.PSQLException
import java.util.UUID

internal class KlageinstanshendelsePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : KlageinstanshendelseRepo {

    internal enum class KlageinstanshendelseType(private val verdi: String) {
        UPROSESSERT("UPROSESSERT"),
        PROSESSERT("PROSESSERT"),
        FEIL("FEIL"),
        ;

        override fun toString(): String = verdi
    }

    override fun lagre(hendelse: UprosessertKlageinstanshendelse) {
        dbMetrics.timeQuery("lagreUprosessertKlageinstanshendelse") {
            Either.catch {
                sessionFactory.withSession { session ->
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
            }.onLeft {
                if (it is PSQLException && it.message!!.contains("duplicate key value violates unique constraint \"klagevedtak_metadata_hendelseid_idx\"")) {
                    // Swallowing this duplicate (part of the dedup routine)
                    // Can't use expression indexes as constraints, so we can't use on conflict ... do nothing: https://stackoverflow.com/questions/16236365/postgresql-conditionally-unique-constraint
                } else {
                    throw it
                }
            }
        }
    }

    override fun lagre(hendelse: ProsessertKlageinstanshendelse, transactionContext: TransactionContext) {
        dbMetrics.timeQuery("lagreProsessertKlageinstanshendelse") {
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
                            "utlest_utfall" to (hendelse as? ProsessertKlageinstanshendelse.KlagebehandlingAvsluttet)?.utfall?.toDatabaseType(),
                            "utlest_journalpostid" to (hendelse as? ProsessertKlageinstanshendelse.KlagebehandlingAvsluttet)?.journalpostIDer,
                            "utlest_klageid" to hendelse.klageId.value,
                            "utlest_mottattKlageinstans" to (hendelse as? ProsessertKlageinstanshendelse.AnkebehandlingOpprettet)?.mottattKlageinstans,
                        ),
                        session = transaction,
                    )
            }
        }
    }

    override fun hentUbehandlaKlageinstanshendelser(): List<UprosessertKlageinstanshendelse> {
        return dbMetrics.timeQuery("hentUbehandlaKlageinstanshendelser") {
            sessionFactory.withSession { session ->
                """
                select * from klageinstanshendelse where type = '${KlageinstanshendelseType.UPROSESSERT}'
                """.trimIndent().hentListe(
                    emptyMap(),
                    session,
                ) { rowToKlage(it) }
            }
        }
    }

    override fun markerSomFeil(id: UUID) {
        dbMetrics.timeQuery("markerKlageSomFeil") {
            sessionFactory.withSession { session ->
                """
                update klageinstanshendelse set type = '${KlageinstanshendelseType.FEIL}' where id = :id
                """.trimIndent().oppdatering(mapOf("id" to id), session)
            }
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

    internal fun hentProsesserteKlageinstanshendelser(
        klageId: KlageId,
        session: Session,
    ): List<ProsessertKlageinstanshendelse> {
        return dbMetrics.timeQuery("hentProsesserteKlageinstanshendelser") {
            """
            select * from klageinstanshendelse where utlest_klageid = :klageid AND type = :type
            """.trimIndent().hentListe(
                mapOf(
                    "klageid" to klageId.value,
                    "type" to KlageinstanshendelseType.PROSESSERT.toString(),
                ),
                session,
            ) { row ->
                require(KlageId(row.uuid("utlest_klageid")) == klageId) {
                    "Fant klageinstanshendelse med klageId ${KlageId(row.uuid("utlest_klageid"))} som ikke matcher forventet klageId $klageId"
                }
                val id = row.uuid("id")
                val opprettet = row.tidspunkt("opprettet")
                val oppgaveId = OppgaveId(row.string("oppgaveid"))
                val utlestUtfall = row.stringOrNull("utlest_utfall")
                if (utlestUtfall != null) {
                    ProsessertKlageinstanshendelse.KlagebehandlingAvsluttet(
                        id = id,
                        opprettet = opprettet,
                        klageId = klageId,
                        utfall = UtfallJson.valueOf(utlestUtfall).toDomain(),
                        journalpostIDer = row.array<String>("utlest_journalpostid").map { JournalpostId(it) },
                        oppgaveId = oppgaveId,
                    )
                } else {
                    ProsessertKlageinstanshendelse.AnkebehandlingOpprettet(
                        id = id,
                        opprettet = opprettet,
                        klageId = klageId,
                        oppgaveId = oppgaveId,
                        mottattKlageinstans = row.tidspunktOrNull("utlest_mottattKlageinstans"),
                    )
                }
            }
        }
    }
}
