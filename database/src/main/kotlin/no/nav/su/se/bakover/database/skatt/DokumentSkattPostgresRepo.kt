package no.nav.su.se.bakover.database.skatt

import kotliquery.Row
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.dokument.PdfA
import no.nav.su.se.bakover.domain.skatt.DokumentSkattRepo
import no.nav.su.se.bakover.domain.skatt.Skattedokument
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

data class DokumentSkattPostgresRepo(
    private val dbMetrics: DbMetrics,
    private val sessionFactory: PostgresSessionFactory,
) : DokumentSkattRepo {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)
    override fun hent(id: UUID): Skattedokument? {
        return sessionFactory.withSession {
            """
            select * from dokument_skatt where id=:id
            """.trimIndent().hent(mapOf("id" to id), it) { it.toSkattedokument() }
        }
    }

    override fun lagre(dok: Skattedokument) {
        sessionFactory.withSession {
            when (dok) {
                is Skattedokument.Generert -> lagre(dok, it)
                is Skattedokument.Journalført -> lagre(dok, it)
            }
        }
    }

    override fun lagre(dok: Skattedokument, txc: SessionContext) {
        txc.withSession {
            when (dok) {
                is Skattedokument.Generert -> lagre(dok, it)
                is Skattedokument.Journalført -> lagre(dok, it)
            }
        }
    }

    private fun lagre(dok: Skattedokument.Generert, session: Session) {
        """
            insert into
                dokument_skatt (id, generertDokument, dokumentjson, sakId, søkersSkatteId, epsSkatteId, vedtakId)
            values
                (:id, :generertDok, to_jsonb(:dokumentjson::jsonb), :sakId, :sokers, :eps, :vedtakId)
        """.trimIndent().insert(
            mapOf(
                "id" to dok.id,
                "generertDok" to dok.generertDokument.getContent(),
                "dokumentjson" to dok.dokumentJson,
                "sakId" to dok.sakid,
                "sokers" to dok.søkersSkatteId,
                "eps" to dok.epsSkatteId,
                "vedtakId" to dok.vedtakid,
            ),
            session,
        )
    }

    override fun hentDokumenterForJournalføring(): List<Skattedokument.Generert> {
        val skattedokumenter = dbMetrics.timeQuery("hentSkattDokumenterForJournalføring") {
            sessionFactory.withSession { session ->
                """
                select * from dokument_skatt
                where journalpostId is null
                order by id asc
                limit 10
                """.trimIndent().hentListe(emptyMap(), session) { it.toSkattedokument() }
            }
        }
        return skattedokumenter.filterIsInstance<Skattedokument.Generert>().also {
            if (it.size != skattedokumenter.size) {
                log.error("Antall hentede dokumenter for journalføring er ikke lik antall som skal bli journalført")
            }
        }
    }

    private fun lagre(dok: Skattedokument.Journalført, session: Session) {
        """
                update
                    dokument_skatt
                set
                    journalpostId=:journalpostId
                where 
                    id=:id
        """.trimIndent().oppdatering(
            mapOf(
                "id" to dok.id,
                "journalpostId" to dok.journalpostid,
            ),
            session,
        )
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

        val generertSkattedokument = Skattedokument.Generert(
            id = id,
            søkersSkatteId = søkers,
            epsSkatteId = eps,
            sakid = sakId,
            vedtakid = vedtakId,
            generertDokument = PdfA(generertDokument),
            dokumentJson = dokumentJson,
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
