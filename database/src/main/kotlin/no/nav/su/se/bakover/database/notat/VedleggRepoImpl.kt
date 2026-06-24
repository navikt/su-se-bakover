package no.nav.su.se.bakover.database.notat

import kotliquery.Row
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.domain.notat.NotatVedlegg
import no.nav.su.se.bakover.domain.notat.VedleggRepo
import java.util.UUID

class VedleggRepoImpl(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : VedleggRepo {

    override fun leggTil(vedlegg: NotatVedlegg) {
        dbMetrics.timeQuery("leggTilNotatVedlegg") {
            sessionFactory.withSession { session ->
                """
                INSERT INTO notat_vedlegg (id, notat_id, filnavn, mime_type, innhold, opprettet)
                VALUES (:id, :notat_id, :filnavn, :mime_type, :innhold, :opprettet)
                """.trimIndent().insert(
                    mapOf(
                        "id" to vedlegg.id,
                        "notat_id" to vedlegg.notatId,
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

    override fun slett(vedleggId: UUID) {
        dbMetrics.timeQuery("slettNotatVedlegg") {
            sessionFactory.withSession { session ->
                """
                DELETE FROM notat_vedlegg WHERE id = :id
                """.trimIndent().oppdatering(
                    mapOf("id" to vedleggId),
                    session,
                )
            }
        }
    }

    override fun hent(vedleggId: UUID): NotatVedlegg? =
        dbMetrics.timeQuery("hentNotatVedlegg") {
            sessionFactory.withSession { session ->
                """
                SELECT * FROM notat_vedlegg WHERE id = :id
                """.trimIndent().hent(
                    mapOf("id" to vedleggId),
                    session,
                ) { rowToVedlegg(it) }
            }
        }

    override fun hentForNotat(notatId: UUID): List<NotatVedlegg> =
        dbMetrics.timeQuery("hentVedleggForNotat") {
            sessionFactory.withSession { session ->
                """
                SELECT * FROM notat_vedlegg WHERE notat_id = :notat_id ORDER BY opprettet
                """.trimIndent().hentListe(
                    mapOf("notat_id" to notatId),
                    session,
                ) { rowToVedlegg(it) }
            }
        }

    private fun rowToVedlegg(row: Row): NotatVedlegg = NotatVedlegg(
        id = row.uuid("id"),
        notatId = row.uuid("notat_id"),
        filnavn = row.string("filnavn"),
        mimeType = row.string("mime_type"),
        innhold = row.bytes("innhold"),
        opprettet = row.tidspunkt("opprettet"),
    )
}
