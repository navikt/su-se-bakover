package no.nav.su.se.bakover.kontrollsamtale.infrastructure.persistence

import kotliquery.Row
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtale
import no.nav.su.se.bakover.kontrollsamtale.domain.KontrollsamtaleRepo
import no.nav.su.se.bakover.kontrollsamtale.domain.Kontrollsamtaler
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
    override fun lagre(kontrollsamtale: Kontrollsamtale, sessionContext: SessionContext?) {
        dbMetrics.timeQuery("lagreKontrollsamtale") {
            sessionFactory.withSession(sessionContext) { session ->
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

    override fun hentForSakId(sakId: UUID, sessionContext: SessionContext?): Kontrollsamtaler {
        return dbMetrics.timeQuery("hentKontrollsamtaleForSakId") {
            sessionFactory.withSession(sessionContext) { session ->
                "select * from kontrollsamtale where sakid=:sakId"
                    .trimIndent()
                    .hentListe(mapOf("sakId" to sakId), session) { it.toKontrollsamtale() }
                    .let { Kontrollsamtaler(sakId, it.sortedBy { it.opprettet.instant }) }
            }
        }
    }

    override fun hentForKontrollsamtaleId(
        kontrollsamtaleId: UUID,
        sessionContext: SessionContext?,
    ): Kontrollsamtale? {
        return dbMetrics.timeQuery("hentForKontrollsamtaleId") {
            sessionFactory.withSession(sessionContext) { session ->
                "select * from kontrollsamtale where id=:id"
                    .trimIndent()
                    .hent(mapOf("id" to kontrollsamtaleId), session) { it.toKontrollsamtale() }
            }
        }
    }

    override fun hentAllePlanlagte(tilOgMed: LocalDate, sessionContext: SessionContext?): List<Kontrollsamtale> {
        return dbMetrics.timeQuery("hentAllePlanlagteKontrollsamtaler") {
            sessionFactory.withSession(sessionContext) { session ->
                "select * from kontrollsamtale where status=:status and innkallingsdato <= :tilOgMed"
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

    /**
     * TODO jah: Dette er en midlertidig fix før vi har tid til å refaktorere hele automatisk stans.
     *
     * @return null hvis det ikke finnes noen rader, ellers den siste fristen før eller på gitt dato.
     */
    override fun hentFristUtløptFørEllerPåDato(fristFørEllerPåDato: LocalDate): LocalDate? {
        return dbMetrics.timeQuery("hentFristFørEllerPåDato") {
            sessionFactory.withSession { session ->
                """
                     SELECT MAX(frist) as frist
                     FROM kontrollsamtale
                     WHERE frist <= :frist
                """.trimIndent()
                    .hent(
                        mapOf(
                            "frist" to fristFørEllerPåDato,
                        ),
                        session,
                    ) { it.localDateOrNull("frist") }
            }
        }
    }

    override fun hentInnkalteKontrollsamtalerMedFristUtløptPåDato(fristPåDato: LocalDate): List<Kontrollsamtale> {
        return dbMetrics.timeQuery("hentKontrollsamtaleFristUtløpt") {
            sessionFactory.withSession { session ->
                "select * from kontrollsamtale where status=:status and frist = :frist"
                    .hentListe(
                        mapOf(
                            "status" to Kontrollsamtalestatus.INNKALT.toString(),
                            "frist" to fristPåDato,
                        ),
                        session,
                    ) { it.toKontrollsamtale() }
            }
        }
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
