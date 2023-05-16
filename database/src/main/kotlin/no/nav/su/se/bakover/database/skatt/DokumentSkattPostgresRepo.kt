package no.nav.su.se.bakover.database.skatt

import no.nav.su.se.bakover.common.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.Session
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.common.persistence.oppdatering
import no.nav.su.se.bakover.domain.skatt.DokumentSkattRepo
import no.nav.su.se.bakover.domain.skatt.Skattedokument

data class DokumentSkattPostgresRepo(
    val sessionFactory: PostgresSessionFactory,
) : DokumentSkattRepo {

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
        """.trimIndent().insert(mapOf(
                "id" to dok.id,
                "generertDok" to dok.generertDokument,
                "dokumentjson" to dok.dokumentJson,
                "sakId" to dok.sakid,
                "sokers" to dok.søkersSkatteId,
                "eps" to dok.epsSkatteId,
                "vedtakId" to dok.vedtakid,
            ),
            session,
        )
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
}
