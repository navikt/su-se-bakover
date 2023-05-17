package no.nav.su.se.bakover.kontrollsamtale.infrastructure.persistence

import kotliquery.Row
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.persistence.hentListe
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.common.persistence.tidspunkt
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleRepo
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtalestatus
import java.time.LocalDate
import java.util.UUID

internal class KontrollsamtalePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : KontrollsamtaleRepo {

    /**
     * upsert - update or insert.
     * Ved update oppdateres: status, innkallingsdato, frist, dokumentId og journalpostIdKontrollnotat.
     */
    override fun lagre(kontrollsamtale: Kontrollsamtale, sessionContext: SessionContext) {
        dbMetrics.timeQuery("lagreKontrollsamtale") {
            sessionContext.withSession { session ->
                (
                    """
                    insert into kontrollsamtale (id, opprettet, sakid, innkallingsdato, status, frist, dokumentid, journalpostId)
                    values (:id, :opprettet, :sakId, :innkallingsdato, :status, :frist, :dokumentid, :journalpostId)
                    on conflict(id)
                    do
                        update set
                            status=:status,
                            innkallingsdato=:innkallingsdato,
                            frist=:frist,
                            dokumentid=:dokumentId,
                            journalpostId=:journalpostId
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
                        "journalpostId" to kontrollsamtale.journalpostIdKontrollnotat,
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
                            "dato" to dato,
                        ),
                        session,
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
            journalpostIdKontrollnotat = stringOrNull("journalpostId")?.let {
                JournalpostId(it)
            },
        )
    }
}
