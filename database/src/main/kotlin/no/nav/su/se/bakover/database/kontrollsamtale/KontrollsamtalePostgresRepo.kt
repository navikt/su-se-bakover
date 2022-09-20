package no.nav.su.se.bakover.database.kontrollsamtale

import kotliquery.Row
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.persistence.hentListe
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.common.persistence.tidspunkt
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtale
import no.nav.su.se.bakover.domain.kontrollsamtale.KontrollsamtaleRepo
import no.nav.su.se.bakover.domain.kontrollsamtale.Kontrollsamtalestatus
import java.time.LocalDate
import java.util.UUID

internal class KontrollsamtalePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : KontrollsamtaleRepo {

    override fun lagre(kontrollsamtale: Kontrollsamtale, sessionContext: SessionContext) {
        dbMetrics.timeQuery("lagreKontrollsamtale") {
            sessionContext.withSession { session ->
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
                    session,
                )
            }
        }
    }

    override fun hentForSakId(sakId: UUID, sessionContext: SessionContext): List<Kontrollsamtale> {
        return dbMetrics.timeQuery("hentKontrollsamtaleForSakId") {
            sessionContext.withSession { session ->
                "select * from kontrollsamtale where sakid=:sakId"
                    .trimIndent()
                    .hentListe(mapOf("sakId" to sakId), session) { it.toKontrollsamtale() }
            }
        }
    }

    override fun hentAllePlanlagte(tilOgMed: LocalDate, sessionContext: SessionContext): List<Kontrollsamtale> {
        return dbMetrics.timeQuery("hentAllePlanlagteKontrollsamtaler") {
            sessionContext.withSession { session ->
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
        }
    }

    override fun hentInnkalteKontrollsamtalerMedFristUtløpt(dato: LocalDate): List<Kontrollsamtale> {
        return dbMetrics.timeQuery("hentKontrollsamtaleFristUtløpt") {
            sessionFactory.withSession { session ->
                "select * from kontrollsamtale where status=:status and frist = :dato"
                    .hentListe(
                        mapOf(
                            "status" to Kontrollsamtalestatus.INNKALT.toString(),
                            "dato" to dato
                        ),
                        session
                    ) { it.toKontrollsamtale() }
            }
        }
    }

    override fun defaultSessionContext(): SessionContext = sessionFactory.newSessionContext()
    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
    }

    private fun Row.toKontrollsamtale(): Kontrollsamtale {
        return Kontrollsamtale(
            id = uuid("id"),
            sakId = uuid("sakid"),
            opprettet = tidspunkt("opprettet"),
            innkallingsdato = localDate("innkallingsdato"),
            frist = localDate("frist"),
            status = Kontrollsamtalestatus.valueOf(string("status")),
            dokumentId = uuidOrNull("dokumentid"),
        )
    }
}
