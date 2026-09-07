package no.nav.su.se.bakover.database.kontrollsamtale

import kotliquery.Row
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotatVedlegg
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotatVedleggRepo
import java.util.UUID

internal class KontrollsamtaleNotatVedleggPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : KontrollsamtaleNotatVedleggRepo {
    override fun lagre(
        vedlegg: KontrollsamtaleNotatVedlegg,
        sessionContext: SessionContext?,
    ) {
        dbMetrics.timeQuery("lagreKontrollsamtaleNotatVedlegg") {
            sessionFactory.withSession(sessionContext) { session ->
                """
                    insert into kontrollsamtale_notat_vedlegg (
                        id,
                        kontrollsamtale_id,
                        filnavn,
                        mime_type,
                        innhold,
                        opprettet
                    )
                    values (
                        :id,
                        :kontrollsamtale_id,
                        :filnavn,
                        :mime_type,
                        :innhold,
                        :opprettet
                    )
                """.trimIndent().insert(
                    mapOf(
                        "id" to vedlegg.id,
                        "kontrollsamtale_id" to vedlegg.kontrollsamtaleId,
                        "filnavn" to vedlegg.filnavn,
                        "mime_type" to vedlegg.mimeType,
                        "innhold" to vedlegg.innhold,
                        "opprettet" to vedlegg.opprettet,
                    ),
                    session,
                )
            }
        }
    }

    override fun slett(vedleggId: UUID, sessionContext: SessionContext?) {
        dbMetrics.timeQuery("slettKontrollsamtaleNotatVedlegg") {
            sessionFactory.withSession(sessionContext) { session ->
                """
                    delete from kontrollsamtale_notat_vedlegg where id = :vedlegg_id
                """.trimIndent().oppdatering(
                    mapOf("vedlegg_id" to vedleggId),
                    session,
                )
            }
        }
    }

    override fun hentForKontrollsamtale(kontrollsamtaleId: UUID, sessionContext: SessionContext?): List<KontrollsamtaleNotatVedlegg> =
        dbMetrics.timeQuery("hentKontrollsamtaleNotatVedlegg") {
            sessionFactory.withSession(sessionContext) { session ->
                """
                    select * from kontrollsamtale_notat_vedlegg where kontrollsamtale_id = :kontrollsamtale_id order by opprettet
                """.trimIndent().hentListe(
                    mapOf("kontrollsamtale_id" to kontrollsamtaleId),
                    session,
                ) { rowToKontrollsamtaleNotatVedlegg(it) }
            }
        }

    private fun rowToKontrollsamtaleNotatVedlegg(
        row: Row,
    ): KontrollsamtaleNotatVedlegg = KontrollsamtaleNotatVedlegg(
        id = row.uuid("id"),
        kontrollsamtaleId = row.uuid("kontrollsamtale_id"),
        filnavn = row.string("filnavn"),
        mimeType = row.string("mime_type"),
        innhold = row.bytes("innhold"),
        opprettet = row.tidspunkt("opprettet"),
    )
}
