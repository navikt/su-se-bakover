package no.nav.su.se.bakover.database.skatt

import no.nav.su.se.bakover.common.persistence.Session
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.common.persistence.oppdatering
import no.nav.su.se.bakover.domain.skatt.Skattedokument

internal object DokumentSkattPostgresRepo {
    fun lagre(dok: Skattedokument, session: Session) {
        when (dok) {
            is Skattedokument.Generert -> lagre(dok, session)
            is Skattedokument.Journalført -> lagre(dok, session)
        }
    }


    private fun lagre(dok: Skattedokument.Generert, session: Session) {

        """
            insert into
                dokument_skatt (id, generertDokument, dokumentjson, sakId, søkersSkatteId, epsSkatteId, vedtakId)
            values
                :id, :generertDok, to_jsonb(:json::json), :sakId, :søkers, :eps, :vedtakId
        """.trimIndent().insert(
            mapOf(
                "id" to dok.id,
                "generertDok" to dok.generertDokument,
                "json" to dok.originalJson,
                "sakId" to dok.sakid,
                "søkers" to dok.søkersSkatteId,
                "eps" to dok.epsSkatteId,
                "vedtak" to dok.vedtakid,
            ), session
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
                "journalpostId" to dok.journalpostid
            ), session
        )
    }
}
