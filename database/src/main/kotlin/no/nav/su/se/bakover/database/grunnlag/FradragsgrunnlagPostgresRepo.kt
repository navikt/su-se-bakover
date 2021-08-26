package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.beregning.PersistertFradrag
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.database.uuid
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.database.withTransaction
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragstype
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID
import javax.sql.DataSource

internal class FradragsgrunnlagPostgresRepo(
    private val dataSource: DataSource,
    private val dbMetrics: DbMetrics,
) : FradragsgrunnlagRepo {

    override fun lagreFradragsgrunnlag(behandlingId: UUID, fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>) {
        dataSource.withTransaction { tx ->
            slettForBehandlingId(behandlingId, tx)
            fradragsgrunnlag.forEach {
                lagre(it, behandlingId, tx)
            }
        }
    }

    internal fun hentFradragsgrunnlag(behandlingId: UUID): List<Grunnlag.Fradragsgrunnlag> {
        return dataSource.withSession { session ->
            hentFradragsgrunnlag(behandlingId, session)
        }
    }

    internal fun hentFradragsgrunnlag(behandlingId: UUID, session: Session): List<Grunnlag.Fradragsgrunnlag> {
        return dbMetrics.timeQuery("hentFradragsgrunnlag") {
            """
                select * from grunnlag_fradrag where behandlingId = :behandlingId
            """.trimIndent()
                .hentListe(
                    mapOf(
                        "behandlingId" to behandlingId,
                    ),
                    session,
                ) {
                    it.toFradragsgrunnlag()
                }
        }
    }

    private fun slettForBehandlingId(behandlingId: UUID, session: Session) {
        """
            delete from grunnlag_fradrag where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                session,
            )
    }

    private fun Row.toFradragsgrunnlag(): Grunnlag.Fradragsgrunnlag {
        return Grunnlag.Fradragsgrunnlag.tryCreate(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            fradrag = PersistertFradrag(
                fradragstype = Fradragstype.valueOf(string("fradragstype")),
                månedsbeløp = double("månedsbeløp"),
                utenlandskInntekt = stringOrNull("utenlandskInntekt")?.let { deserialize(it) },
                periode = Periode.create(fraOgMed = localDate("fraOgMed"), tilOgMed = localDate("tilOgMed")),
                tilhører = FradragTilhører.valueOf(string("tilhører")),
            ),
        ).orNull()!!
    }

    private fun lagre(fradragsgrunnlag: Grunnlag.Fradragsgrunnlag, behandlingId: UUID, session: Session) {
        """
            insert into grunnlag_fradrag
            (
                id,
                opprettet,
                behandlingId,
                fraOgMed,
                tilOgMed,
                fradragstype,
                månedsbeløp,
                utenlandskInntekt,
                tilhører
            ) values
            (
                :id,
                :opprettet,
                :behandlingId,
                :fraOgMed,
                :tilOgMed,
                :fradragstype,
                :manedsbelop,
                to_json(:utenlandskInntekt::json),
                :tilhorer
            )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to fradragsgrunnlag.id,
                    "opprettet" to fradragsgrunnlag.opprettet,
                    "behandlingId" to behandlingId,
                    "fraOgMed" to fradragsgrunnlag.fradrag.periode.fraOgMed,
                    "tilOgMed" to fradragsgrunnlag.fradrag.periode.tilOgMed,
                    "fradragstype" to fradragsgrunnlag.fradrag.fradragstype,
                    "manedsbelop" to fradragsgrunnlag.fradrag.månedsbeløp,
                    "utenlandskInntekt" to objectMapper.writeValueAsString(fradragsgrunnlag.fradrag.utenlandskInntekt),
                    "tilhorer" to fradragsgrunnlag.fradrag.tilhører,
                ),
                session,
            )
    }
}
