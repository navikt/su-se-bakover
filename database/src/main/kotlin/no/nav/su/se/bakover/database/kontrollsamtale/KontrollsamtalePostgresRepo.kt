package no.nav.su.se.bakover.database.kontrollsamtale

import kotliquery.Row
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.database.PostgresSessionFactory
import no.nav.su.se.bakover.database.PostgresTransactionContext.Companion.withTransaction
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.uuidOrNull
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtale
import no.nav.su.se.bakover.domain.kontrollsamtale.KontrollsamtaleRepo
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtalestatus
import java.time.LocalDate
import java.util.UUID

internal class KontrollsamtalePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
) : KontrollsamtaleRepo {
    override fun lagre(kontrollsamtale: Kontrollsamtale, transactionContext: TransactionContext) {
        transactionContext.withTransaction { transaction ->
            (
                """
                    insert into kontrollsamtale (id, opprettet, sakid, innkallingsdato, status, frist, dokumentid)
                    values (:id, :opprettet, :sakId, :innkallingsdato, :status, :frist, :dokumentid)
                    on conflict(id)
                    do
                        update set 
                            status=:status,
                            innkallingsdato=:innkallingsdato,
                            frist=:frist,
                            dokumentid=:dokumentId
                """
                ).trimIndent().insert(
                mapOf(
                    "id" to kontrollsamtale.id,
                    "opprettet" to kontrollsamtale.opprettet,
                    "sakId" to kontrollsamtale.sakId,
                    "innkallingsdato" to kontrollsamtale.innkallingsdato,
                    "status" to kontrollsamtale.status.toString(),
                    "frist" to kontrollsamtale.frist,
                    "dokumentId" to kontrollsamtale.dokumentId,
                ),
                transaction,
            )
        }
    }

    override fun hent(sakId: UUID): List<Kontrollsamtale> =
        sessionFactory.withSession { session ->
            "select * from kontrollsamtale where sakid=:sakId"
                .trimIndent()
                .hentListe(mapOf("sakId" to sakId), session) { it.toKontrollsamtale() }
        }

    override fun hentAllePlanlagte(tilOgMed: LocalDate): List<Kontrollsamtale> =
        sessionFactory.withSession { session ->
            "select * from kontrollsamtale where status=:status and innkallingsdato <= :tilOgMed"
                .trimIndent()
                .hentListe(
                    mapOf(
                        "status" to Kontrollsamtalestatus.PLANLAGT_INNKALLING.toString(),
                        "tilOgMed" to tilOgMed,
                    ),
                    session,
                ) { it.toKontrollsamtale() }
        }

    override fun defaultTransactionContext(): TransactionContext = sessionFactory.newTransactionContext()

    private fun Row.toKontrollsamtale(): Kontrollsamtale =
        Kontrollsamtale(
            id = uuid("id"),
            sakId = uuid("sakid"),
            opprettet = tidspunkt("opprettet"),
            innkallingsdato = localDate("innkallingsdato"),
            frist = localDate("frist"),
            status = Kontrollsamtalestatus.valueOf(string("status")),
            dokumentId = uuidOrNull("dokumentid"),
        )
}
