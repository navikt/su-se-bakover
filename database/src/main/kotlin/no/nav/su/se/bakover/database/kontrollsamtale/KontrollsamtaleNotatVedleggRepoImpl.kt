package no.nav.su.se.bakover.database.kontrollsamtale

import kotliquery.Row
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.antall
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotatVedlegg
import no.nav.su.se.bakover.domain.kontrollnotat.KontrollsamtaleNotatVedleggRepo
import java.util.UUID

class KontrollsamtaleNotatVedleggRepoImpl(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : KontrollsamtaleNotatVedleggRepo {

    override fun leggTil(vedlegg: KontrollsamtaleNotatVedlegg) {
        dbMetrics.timeQuery(label = "leggTilKontrollsamtaleNotatVedlegg") {
            sessionFactory.withSession { session ->
                """
                INSERT INTO kontrollsamtale_notat_vedlegg (id, kontrollsamtale_notat_id, filnavn, mime_type, innhold, opprettet)
                VALUES (:id, :kontrollsamtale_notat_id, :filnavn, :mime_type, :innhold, :opprettet)
                """.trimIndent().insert(
                    mapOf(
                        "id" to vedlegg.id,
                        "kontrollsamtale_notat_id" to vedlegg.kontrollsamtaleNotatId,
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
        dbMetrics.timeQuery(label = "slettKontrollsamtaleNotatVedlegg") {
            sessionFactory.withSession { session ->
                """
                DELETE FROM kontrollsamtale_notat_vedlegg WHERE id = :id
                """.trimIndent().insert(
                    mapOf("id" to vedleggId),
                    session,
                )
            }
        }
    }

    override fun hent(vedleggId: UUID): KontrollsamtaleNotatVedlegg? =
        dbMetrics.timeQuery(label = "hentKontrollsamtaleNotatVedlegg") {
            sessionFactory.withSession { session ->
                """
                SELECT * FROM kontrollsamtale_notat_vedlegg WHERE id = :id
                """.trimIndent().hent(
                    mapOf("id" to vedleggId),
                    session,
                ) {
                    rowToVedlegg(it)
                }
            }
        }

    override fun hentForKontrollsamtaleNotat(kontrollsamtaleNotatId: UUID): List<KontrollsamtaleNotatVedlegg> =
        dbMetrics.timeQuery(label = "hentVedleggForKontrollsamtaleNotat") {
            sessionFactory.withSession { session ->
                """
                SELECT * FROM kontrollsamtale_notat_vedlegg WHERE kontrollsamtale_notat_id = :kontrollsamtale_notat_id ORDER BY opprettet
                """.trimIndent().hentListe(
                    mapOf("kontrollsamtale_notat_id" to kontrollsamtaleNotatId),
                    session,
                ) {
                    rowToVedlegg(it)
                }
            }
        }

    override fun hentAntallVedlegg(kontrollsamtaleNotatId: UUID): Int =
        dbMetrics.timeQuery(label = "hentAntallKontrollsamtaleNotatVedlegg") {
            sessionFactory.withSession { session ->
                """
                SELECT COUNT(*) AS count FROM kontrollsamtale_notat_vedlegg WHERE kontrollsamtale_notat_id = :kontrollsamtale_notat_id
                """.trimIndent().antall(
                    mapOf("kontrollsamtale_notat_id" to kontrollsamtaleNotatId),
                    session,
                ).toInt()
            }
        }

    private fun rowToVedlegg(row: Row): KontrollsamtaleNotatVedlegg {
        return KontrollsamtaleNotatVedlegg(
            id = row.uuid("id"),
            kontrollsamtaleNotatId = row.uuid("kontrollsamtale_notat_id"),
            filnavn = row.string("filnavn"),
            mimeType = row.string("mime_type"),
            innhold = row.bytes("innhold"),
            opprettet = row.tidspunkt("opprettet"),
        )
    }
}
