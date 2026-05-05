package no.nav.su.se.bakover.database.personhendelse

import kotliquery.Row
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunktOrNull
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.personhendelse.Fødselsnummerhendelse
import no.nav.su.se.bakover.domain.personhendelse.FødselsnummerhendelseRepo
import java.time.Clock
import java.util.UUID

internal class FødselsnummerhendelsePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val clock: Clock,
) : FødselsnummerhendelseRepo {

    override fun lagre(sakId: UUID) {
        dbMetrics.timeQuery("lagreFødselsnummerhendelse") {
            sessionFactory.withSession { session ->
                """
                insert into fnr_oppdatering_innboks (id, sak_id, opprettet)
                values (:id, :sakId, :opprettet)
                """.trimIndent().insert(
                    mapOf(
                        "id" to UUID.randomUUID(),
                        "sakId" to sakId,
                        "opprettet" to Tidspunkt.now(clock),
                    ),
                    session,
                )
            }
        }
    }

    override fun hentUbehandlede(limit: Int): List<Fødselsnummerhendelse> {
        return dbMetrics.timeQuery("hentUbehandledeFødselsnummerhendelser") {
            sessionFactory.withSession { session ->
                """
                select * from fnr_oppdatering_innboks
                where prosessert is null
                order by opprettet asc
                limit :limit
                """.trimIndent().hentListe(mapOf("limit" to limit), session) { it.toDomain() }
            }
        }
    }

    override fun markerProsessert(id: UUID, tidspunkt: Tidspunkt, transactionContext: TransactionContext) {
        sessionFactory.withSession(transactionContext) { session ->
            """
            update fnr_oppdatering_innboks set prosessert = :prosessert where id = :id
            """.trimIndent().oppdatering(
                mapOf("id" to id, "prosessert" to tidspunkt),
                session,
            )
        }
    }

    private fun Row.toDomain(): Fødselsnummerhendelse = Fødselsnummerhendelse(
        id = uuid("id"),
        sakId = uuid("sak_id"),
        opprettet = tidspunkt("opprettet"),
        prosessert = tidspunktOrNull("prosessert"),
    )
}
