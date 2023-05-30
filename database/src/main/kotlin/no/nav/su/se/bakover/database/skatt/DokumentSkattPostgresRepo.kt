package no.nav.su.se.bakover.database.skatt

import kotliquery.Row
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.tid.Tidspunkt
import no.nav.su.se.bakover.domain.skatt.DokumentSkattRepo
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

data class DokumentSkattPostgresRepo(
    private val dbMetrics: DbMetrics,
    private val sessionFactory: PostgresSessionFactory,
    private val clock: Clock,
) : DokumentSkattRepo {
    private val henterDokumenterLimit = 10
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    override fun hent(id: UUID): Skattedokument? {
        return sessionFactory.withSession {
            """
            select * from dokument_skatt where id=:id
            """.trimIndent().hent(mapOf("id" to id), it) { it.toSkattedokument() }
        }
    }

    override fun lagre(dok: Skattedokument) {
        sessionFactory.withSession { lagre(dok, it) }
    }

    override fun lagre(dok: Skattedokument, session: SessionContext) {
        session.withSession { lagre(dok, it) }
    }

    private fun lagre(dok: Skattedokument, session: Session) {
        """
            insert into
                dokument_skatt (id, opprettet, generertDokument, dokumentjson, sakId, søkersSkatteId, epsSkatteId, vedtakId, journalpostId, skattedataHentet)
            values
                (:id, :opprettet, :generertDok, to_jsonb(:dokumentjson::jsonb), :sakId, :sokers, :eps, :vedtakId, :journalpostId, :skattedataHentet)
            on conflict (id) do update set
                opprettet=:opprettet, generertDokument=:generertDok, dokumentjson=to_jsonb(:dokumentjson::jsonb), sakId=:sakId, søkersSkatteId=:sokers, epsSkatteId=:eps, vedtakId=:vedtakId, journalpostId=:journalpostId, skattedataHentet=:skattedataHentet
        """.trimIndent().insert(
            mapOf(
                "id" to dok.id,
                "opprettet" to Tidspunkt.now(clock),
                "generertDok" to dok.generertDokument.getContent(),
                "dokumentjson" to dok.dokumentJson,
                "sakId" to dok.sakid,
                "sokers" to dok.søkersSkatteId,
                "eps" to dok.epsSkatteId,
                "vedtakId" to dok.vedtakid,
                "journalpostId" to dok.journalpostid,
                "skattedataHentet" to dok.skattedataHentet,
            ),
            session,
        )
    }

    /**
     * Henter max antall dokumenter basert på [henterDokumenterLimit]
     */
    override fun hentDokumenterForJournalføring(): List<Skattedokument.Generert> {
        val skattedokumenter = dbMetrics.timeQuery("hentSkattDokumenterForJournalføring") {
            sessionFactory.withSession { session ->
                """
                select * from dokument_skatt
                where journalpostId is null
                order by opprettet asc
                limit :limit
                """.trimIndent().hentListe(mapOf("limit" to henterDokumenterLimit), session) { it.toSkattedokument() }
            }
        }
        return skattedokumenter.filterIsInstance<Skattedokument.Generert>().also {
            if (it.size != skattedokumenter.size) {
                val hentedeDokumenterSomIkkeSkalBliJournalført = skattedokumenter.map { it.id }.minus(it.map { it.id })
                log.error("Antall hentede dokumenter for journalføring er ikke lik antall som skal bli journalført. id'er $hentedeDokumenterSomIkkeSkalBliJournalført")
            }
        }
    }

    private fun Row.toSkattedokument(): Skattedokument {
        val id = uuid("id")
        val søkers = uuid("søkersskatteid")
        val eps = uuidOrNull("epsskatteid")
        val sakId = uuid("sakid")
        val vedtakId = uuid("vedtakId")
        val dokumentJson = string("dokumentjson")
        val generertDokument = bytes("generertDokument")
        val journalpostId = stringOrNull("journalpostId")
        val skattedataHentet = tidspunkt("skattedataHentet")

        val generertSkattedokument = Skattedokument.Generert(
            id = id,
            søkersSkatteId = søkers,
            epsSkatteId = eps,
            sakid = sakId,
            vedtakid = vedtakId,
            generertDokument = PdfA(generertDokument),
            dokumentJson = dokumentJson,
            skattedataHentet = skattedataHentet,
        )

        return when (journalpostId) {
            null -> generertSkattedokument
            else -> Skattedokument.Journalført(
                generert = generertSkattedokument,
                journalpostid = JournalpostId(journalpostId),
            )
        }
    }
}
