package no.nav.su.se.bakover.database.dokument

import com.fasterxml.jackson.module.kotlin.readValue
import kotliquery.Row
import no.nav.su.se.bakover.common.Tidspunkt
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.uuidOrNull
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.brev.BrevbestillingId
import no.nav.su.se.bakover.domain.dokument.Dokument
import no.nav.su.se.bakover.domain.dokument.DokumentRepo
import no.nav.su.se.bakover.domain.dokument.Dokumentdistribusjon
import no.nav.su.se.bakover.domain.eksterneiverksettingssteg.JournalføringOgBrevdistribusjon
import no.nav.su.se.bakover.domain.journal.JournalpostId
import java.util.UUID
import javax.sql.DataSource

internal class DokumentPostgresRepo(
    private val dataSource: DataSource,
) : DokumentRepo {

    override fun lagre(dokument: Dokument.MedMetadata) {
        dataSource.withTransaction { tx ->
            """
                insert into dokument(id, opprettet, sakId, generertDokument, generertDokumentJson, type, tittel, søknadId, vedtakId, revurderingId, bestillbrev) 
                values (:id, :opprettet, :sakId, :generertDokument, to_json(:generertDokumentJson::json), :type, :tittel, :soknadId, :vedtakId, :revurderingId, :bestillbrev)
            """.trimIndent()
                .insert(
                    mapOf(
                        "id" to dokument.id,
                        "opprettet" to dokument.opprettet,
                        "sakId" to dokument.metadata.sakId,
                        "generertDokument" to dokument.generertDokument,
                        "generertDokumentJson" to objectMapper.writeValueAsString(dokument.generertDokumentJson),
                        "type" to when (dokument) {
                            is Dokument.MedMetadata.Informasjon -> DokumentKategori.INFORMASJON
                            is Dokument.MedMetadata.Vedtak -> DokumentKategori.VEDTAK
                        }.toString(),
                        "tittel" to dokument.tittel,
                        "soknadId" to dokument.metadata.søknadId,
                        "vedtakId" to dokument.metadata.vedtakId,
                        "revurderingId" to dokument.metadata.revurderingId,
                        "bestillbrev" to dokument.metadata.bestillBrev,
                    ),
                    tx,
                )

            if (dokument.metadata.bestillBrev)
                lagreDokumentdistribusjon(
                    dokumentdistribusjon = Dokumentdistribusjon(
                        id = UUID.randomUUID(),
                        opprettet = dokument.opprettet,
                        endret = dokument.opprettet,
                        dokument = dokument,
                        journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.IkkeJournalførtEllerDistribuert,
                    ),
                    tx,
                )
        }
    }

    override fun hentDokument(id: UUID): Dokument.MedMetadata? {
        return dataSource.withSession { session ->
            hentDokument(id, session)
        }
    }

    override fun hentForSak(id: UUID): List<Dokument.MedMetadata> {
        return dataSource.withSession { session ->
            """
                select * from dokument where sakId = :id
            """.trimIndent()
                .hentListe(mapOf("id" to id), session) {
                    it.toDokument()
                }
        }
    }

    override fun hentForSøknad(id: UUID): List<Dokument.MedMetadata> {
        return dataSource.withSession { session ->
            """
                select * from dokument where søknadId = :id
            """.trimIndent()
                .hentListe(mapOf("id" to id), session) {
                    it.toDokument()
                }
        }
    }

    override fun hentForVedtak(id: UUID): List<Dokument.MedMetadata> {
        return dataSource.withSession { session ->
            """
                select * from dokument where vedtakId = :id
            """.trimIndent()
                .hentListe(mapOf("id" to id), session) {
                    it.toDokument()
                }
        }
    }

    override fun hentForRevurdering(id: UUID): List<Dokument.MedMetadata> {
        return dataSource.withSession { session ->
            """
                select * from dokument where revurderingId = :id
            """.trimIndent()
                .hentListe(mapOf("id" to id), session) {
                    it.toDokument()
                }
        }
    }

    override fun hentDokumentdistribusjon(id: UUID): Dokumentdistribusjon? {
        return dataSource.withSession { session ->
            """
                select * from dokument_distribusjon where id = :id
            """.trimIndent()
                .hent(
                    mapOf(
                        "id" to id,
                    ),
                    session,
                ) {
                    it.toDokumentdistribusjon(session)
                }
        }
    }

    override fun hentDokumenterForDistribusjon(): List<Dokumentdistribusjon> {
        return dataSource.withSession { session ->
            """
                select * from dokument_distribusjon 
                where journalpostId is null or brevbestillingId is null
                order by opprettet asc
                limit 10
            """.trimIndent()
                .hentListe(emptyMap(), session) {
                    it.toDokumentdistribusjon(session)
                }
        }
    }

    override fun oppdaterDokumentdistribusjon(dokumentdistribusjon: Dokumentdistribusjon) {
        dataSource.withSession { session ->
            """
                update dokument_distribusjon set 
                    journalpostId = :journalpostId,
                    brevbestillingId = :brevbestillingId,
                    endret = :endret
                where id = :id    
            """.trimIndent()
                .oppdatering(
                    mapOf(
                        "id" to dokumentdistribusjon.id,
                        "journalpostId" to JournalføringOgBrevdistribusjon.iverksattJournalpostId(
                            dokumentdistribusjon.journalføringOgBrevdistribusjon,
                        ),
                        "brevbestillingId" to JournalføringOgBrevdistribusjon.iverksattBrevbestillingId(
                            dokumentdistribusjon.journalføringOgBrevdistribusjon,
                        ),
                        "endret" to Tidspunkt.now(),
                    ),
                    session,
                )
        }
    }

    private fun lagreDokumentdistribusjon(dokumentdistribusjon: Dokumentdistribusjon, tx: TransactionalSession) {
        """
            insert into dokument_distribusjon(id, opprettet, endret, dokumentId, journalpostId, brevbestillingId)
            values (:id, :opprettet, :endret, :dokumentId, :journalpostId, :brevbestillingId)
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to dokumentdistribusjon.id,
                    "opprettet" to dokumentdistribusjon.opprettet,
                    "endret" to dokumentdistribusjon.endret,
                    "dokumentId" to dokumentdistribusjon.dokument.id,
                ),
                tx,
            )
    }

    private fun hentDokument(id: UUID, session: Session) =
        """
            select * from dokument where id = :id
        """.trimIndent()
            .hent(mapOf("id" to id), session) {
                it.toDokument()
            }

    private fun Row.toDokument(): Dokument.MedMetadata {
        val type = DokumentKategori.valueOf(string("type"))
        val id = uuid("id")
        val opprettet = tidspunkt("opprettet")
        val innhold = bytes("generertDokument")
        val request = objectMapper.readValue<String>(string("generertDokumentJson"))
        val sakId = uuid("sakid")
        val søknadId = uuidOrNull("søknadId")
        val vedtakId = uuidOrNull("vedtakId")
        val revurderingId = uuidOrNull("revurderingId")
        val tittel = string("tittel")
        val bestillbrev = boolean("bestillbrev")
        return when (type) {
            DokumentKategori.INFORMASJON -> Dokument.MedMetadata.Informasjon(
                id = id,
                opprettet = opprettet,
                tittel = tittel,
                generertDokument = innhold,
                generertDokumentJson = request,
                metadata = Dokument.Metadata(
                    sakId = sakId,
                    søknadId = søknadId,
                    vedtakId = vedtakId,
                    revurderingId = revurderingId,
                    bestillBrev = bestillbrev,
                ),
            )
            DokumentKategori.VEDTAK -> Dokument.MedMetadata.Vedtak(
                id = id,
                opprettet = opprettet,
                tittel = tittel,
                generertDokument = innhold,
                generertDokumentJson = request,
                metadata = Dokument.Metadata(
                    sakId = sakId,
                    søknadId = søknadId,
                    vedtakId = vedtakId,
                    revurderingId = revurderingId,
                    bestillBrev = bestillbrev,
                ),
            )
        }
    }

    private enum class DokumentKategori {
        INFORMASJON,
        VEDTAK,
    }

    private fun Row.toDokumentdistribusjon(session: Session): Dokumentdistribusjon {
        return Dokumentdistribusjon(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            endret = tidspunkt("endret"),
            dokument = hentDokument(uuid("dokumentId"), session)!!,
            journalføringOgBrevdistribusjon = JournalføringOgBrevdistribusjon.fromId(
                iverksattJournalpostId = stringOrNull("journalpostid")?.let { JournalpostId(it) },
                iverksattBrevbestillingId = stringOrNull("brevbestillingid")?.let { BrevbestillingId(it) },
            ),
        )
    }
}
