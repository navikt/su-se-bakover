package no.nav.su.se.bakover.dokument.infrastructure.database

import dokument.domain.Dokument
import dokument.domain.DokumentRepo
import dokument.domain.Dokumentdistribusjon
import dokument.domain.JournalføringOgBrevdistribusjon
import dokument.domain.brev.BrevbestillingId
import dokument.domain.distribuering.Distribueringsadresse
import dokument.domain.hendelser.DokumentHendelseRepo
import kotliquery.Row
import no.nav.su.se.bakover.common.domain.PdfA
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.Session
import no.nav.su.se.bakover.common.infrastructure.persistence.TransactionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.journal.JournalpostId
import no.nav.su.se.bakover.common.persistence.TransactionContext
import no.nav.su.se.bakover.common.tid.Tidspunkt
import java.time.Clock
import java.util.UUID

class DokumentPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
    private val clock: Clock,
    private val dokumentHendelseRepo: DokumentHendelseRepo,
) : DokumentRepo {

    private val joinDokumentOgDistribusjonQuery =
        "select d.*, dd.journalpostid, dd.brevbestillingid from dokument d left join dokument_distribusjon dd on dd.dokumentid = d.id where d.duplikatAv is null"

    override fun lagre(dokument: Dokument.MedMetadata, transactionContext: TransactionContext?) {
        dbMetrics.timeQuery("lagreDokumentMedMetadata") {
            sessionFactory.withTransaction(transactionContext) { tx ->
                """
                insert into dokument(id, opprettet, sakId, generertDokument, generertDokumentJson, type, tittel, søknadId, vedtakId, revurderingId, klageId, distribueringsadresse)
                values (:id, :opprettet, :sakId, :generertDokument, to_json(:generertDokumentJson::json), :type, :tittel, :soknadId, :vedtakId, :revurderingId, :klageId, :distribueringsadresse::jsonb)
                """.trimIndent()
                    .insert(
                        mapOf(
                            "id" to dokument.id,
                            "opprettet" to dokument.opprettet,
                            "sakId" to dokument.metadata.sakId,
                            "generertDokument" to dokument.generertDokument.getContent(),
                            // Dette er allerede gyldig json lagret som en String.
                            "generertDokumentJson" to dokument.generertDokumentJson,
                            "type" to when (dokument) {
                                is Dokument.MedMetadata.Informasjon.Viktig -> DokumentKategori.INFORMASJON_VIKTIG
                                is Dokument.MedMetadata.Informasjon.Annet -> DokumentKategori.INFORMASJON_ANNET
                                is Dokument.MedMetadata.Vedtak -> DokumentKategori.VEDTAK
                            }.toString(),
                            "tittel" to dokument.tittel,
                            "soknadId" to dokument.metadata.søknadId,
                            "vedtakId" to dokument.metadata.vedtakId,
                            "revurderingId" to dokument.metadata.revurderingId,
                            "klageId" to dokument.metadata.klageId,
                            "distribueringsadresse" to dokument.distribueringsadresse?.toDbJson(),
                        ),
                        tx,
                    )

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
    }

    override fun hentDokument(id: UUID): Dokument.MedMetadata? {
        return dbMetrics.timeQuery("hentDokumentMedMetadataForDokumentId") {
            sessionFactory.withSession { session ->
                hentDokument(id, session)
            }
        }
    }

    override fun hentForSak(sakId: UUID): List<Dokument.MedMetadata> {
        return dbMetrics.timeQuery("hentDokumentMedMetadataForSakId") {
            sessionFactory.withSessionContext { ct ->
                val dokumenterFraHendelser = dokumentHendelseRepo.hentDokumentHendelserForSakId(sakId, ct).tilDokumenterMedMetadata(
                    hentDokumentForHendelseId = { hendelseId ->
                        dokumentHendelseRepo.hentFilFor(hendelseId, ct)
                    },
                )
                (
                    ct.withSession {
                        """
                $joinDokumentOgDistribusjonQuery and sakId = :id
                        """.trimIndent()
                            .hentListe(mapOf("id" to sakId), it) {
                                it.toDokumentMedStatus()
                            }
                    } + dokumenterFraHendelser
                    ).sortedBy { it.opprettet.instant }
            }
        }
    }

    override fun hentForSøknad(id: UUID): List<Dokument.MedMetadata> {
        return dbMetrics.timeQuery("hentDokumentMedMetadataForSøknadId") {
            sessionFactory.withSession { session ->
                """
                $joinDokumentOgDistribusjonQuery and søknadId = :id
                """.trimIndent()
                    .hentListe(mapOf("id" to id), session) {
                        it.toDokumentMedStatus()
                    }
            }
        }
    }

    override fun hentForVedtak(id: UUID): List<Dokument.MedMetadata> {
        return dbMetrics.timeQuery("hentDokumentMedMetadataForVedtakId") {
            sessionFactory.withSession { session ->
                """
                $joinDokumentOgDistribusjonQuery and vedtakId = :id
                """.trimIndent()
                    .hentListe(mapOf("id" to id), session) {
                        it.toDokumentMedStatus()
                    }
            }
        }
    }

    override fun hentForRevurdering(id: UUID): List<Dokument.MedMetadata> {
        return dbMetrics.timeQuery("hentDokumentMedMetadataForRevurderingId") {
            sessionFactory.withSession { session ->
                """
                $joinDokumentOgDistribusjonQuery and revurderingId = :id
                """.trimIndent()
                    .hentListe(mapOf("id" to id), session) {
                        it.toDokumentMedStatus()
                    }
            }
        }
    }

    override fun hentForKlage(id: UUID): List<Dokument.MedMetadata> {
        return dbMetrics.timeQuery("hentDokumentMedMetadataForKlageId") {
            sessionFactory.withSession { session ->
                """
                $joinDokumentOgDistribusjonQuery and klageId = :id
                """.trimIndent()
                    .hentListe(mapOf("id" to id), session) {
                        it.toDokumentMedStatus()
                    }
            }
        }
    }

    override fun hentDokumentdistribusjon(id: UUID): Dokumentdistribusjon? {
        return dbMetrics.timeQuery("hentDokumentdistribusjon") {
            sessionFactory.withSession { session ->
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
    }

    override fun hentDokumentdistribusjonForDokumentId(dokumentId: UUID): Dokumentdistribusjon? {
        return dbMetrics.timeQuery("hentDokumentdistribusjon") {
            sessionFactory.withSession { session ->
                """
                select * from dokument_distribusjon where dokumentId = :dokumentId
                """.trimIndent()
                    .hent(
                        mapOf(
                            "dokumentId" to dokumentId,
                        ),
                        session,
                    ) {
                        it.toDokumentdistribusjon(session)
                    }
            }
        }
    }

    /**
     * Henter max antall dokumenter basert på [antallSomSkalHentes]
     */
    override fun hentDokumenterForDistribusjon(antallSomSkalHentes: Int): List<Pair<Dokumentdistribusjon, Distribueringsadresse?>> {
        return dbMetrics.timeQuery("hentDokumenterForDistribusjon") {
            sessionFactory.withSession { session ->
                """
                select dd.*, d.distribueringsadresse from dokument_distribusjon dd join dokument d on dd.dokumentid = d.id
                where brevbestillingId is null and journalpostId is not null
                order by dd.opprettet asc
                limit :limit
                """.trimIndent()
                    .hentListe(mapOf("limit" to antallSomSkalHentes), session) {
                        it.toDokumentdistribusjon(session) to it.stringOrNull("distribueringsadresse")
                            ?.let { deserializeDistribueringsadresse(it) }
                    }
            }
        }
    }

    /**
     * Henter max antall dokumenter basert på [antallSomSkalHentes]
     */
    override fun hentDokumenterForJournalføring(antallSomSkalHentes: Int): List<Dokumentdistribusjon> {
        return dbMetrics.timeQuery("hentDokumenterForJournalføring") {
            sessionFactory.withSession { session ->
                """
                select * from dokument_distribusjon
                where journalpostId is null
                order by opprettet asc
                limit :limit
                """.trimIndent()
                    .hentListe(mapOf("limit" to antallSomSkalHentes), session) { it.toDokumentdistribusjon(session) }
            }
        }
    }

    override fun oppdaterDokumentdistribusjon(dokumentdistribusjon: Dokumentdistribusjon) {
        dbMetrics.timeQuery("oppdaterDokumentdistribusjon") {
            sessionFactory.withSession { session ->
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
                            "endret" to Tidspunkt.now(clock),
                        ),
                        session,
                    )
            }
        }
    }

    override fun defaultTransactionContext(): TransactionContext {
        return sessionFactory.newTransactionContext()
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
            $joinDokumentOgDistribusjonQuery and d.id = :id
        """.trimIndent()
            .hent(mapOf("id" to id), session) {
                it.toDokumentMedStatus()
            }

    private fun Row.toDokumentMedStatus(): Dokument.MedMetadata {
        val type = DokumentKategori.valueOf(string("type"))
        val id = uuid("id")
        val opprettet = tidspunkt("opprettet")
        val innhold = PdfA(bytes("generertDokument"))
        val request = string("generertDokumentJson")
        val sakId = uuid("sakid")
        val søknadId = uuidOrNull("søknadId")
        val vedtakId = uuidOrNull("vedtakId")
        val revurderingId = uuidOrNull("revurderingId")
        val klageId = uuidOrNull("klageId")
        val tittel = string("tittel")
        val brevbestillingId = stringOrNull("brevbestillingid")
        val journalpostId = stringOrNull("journalpostid")
        val distribueringsadresse = stringOrNull("distribueringsadresse")?.let { deserializeDistribueringsadresse(it) }

        return when (type) {
            DokumentKategori.INFORMASJON_VIKTIG -> Dokument.MedMetadata.Informasjon.Viktig(
                id = id,
                opprettet = opprettet,
                tittel = tittel,
                generertDokument = innhold,
                generertDokumentJson = request,
                distribueringsadresse = distribueringsadresse,
                metadata = Dokument.Metadata(
                    sakId = sakId,
                    søknadId = søknadId,
                    vedtakId = vedtakId,
                    revurderingId = revurderingId,
                    klageId = klageId,
                    brevbestillingId = brevbestillingId,
                    journalpostId = journalpostId,
                ),
            )

            DokumentKategori.INFORMASJON_ANNET -> Dokument.MedMetadata.Informasjon.Annet(
                id = id,
                opprettet = opprettet,
                tittel = tittel,
                generertDokument = innhold,
                generertDokumentJson = request,
                distribueringsadresse = distribueringsadresse,
                metadata = Dokument.Metadata(
                    sakId = sakId,
                    søknadId = søknadId,
                    vedtakId = vedtakId,
                    revurderingId = revurderingId,
                    klageId = klageId,
                    brevbestillingId = brevbestillingId,
                    journalpostId = journalpostId,
                ),
            )

            DokumentKategori.VEDTAK -> Dokument.MedMetadata.Vedtak(
                id = id,
                opprettet = opprettet,
                tittel = tittel,
                generertDokument = innhold,
                generertDokumentJson = request,
                distribueringsadresse = distribueringsadresse,
                metadata = Dokument.Metadata(
                    sakId = sakId,
                    søknadId = søknadId,
                    vedtakId = vedtakId,
                    revurderingId = revurderingId,
                    klageId = klageId,
                    brevbestillingId = brevbestillingId,
                    journalpostId = journalpostId,
                ),
            )
        }
    }

    private enum class DokumentKategori {
        INFORMASJON_VIKTIG,
        INFORMASJON_ANNET,
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
