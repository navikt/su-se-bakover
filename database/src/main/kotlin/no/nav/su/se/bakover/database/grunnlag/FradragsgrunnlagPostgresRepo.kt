package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.database.beregning.PersistertFradrag
import no.nav.su.se.bakover.database.hentListe
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.domain.beregning.fradrag.FradragTilhører
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradragskategori
import no.nav.su.se.bakover.domain.grunnlag.Grunnlag
import java.util.UUID

internal class FradragsgrunnlagPostgresRepo(
    private val dbMetrics: DbMetrics,
) {
    internal fun lagreFradragsgrunnlag(
        behandlingId: UUID,
        fradragsgrunnlag: List<Grunnlag.Fradragsgrunnlag>,
        tx: TransactionalSession,
    ) {
        dbMetrics.timeQuery("lagreFradragsgrunnlag") {
            slettForBehandlingId(behandlingId, tx)
            fradragsgrunnlag.forEach {
                lagre(it, behandlingId, tx)
            }
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

    private fun slettForBehandlingId(behandlingId: UUID, tx: TransactionalSession) {
        """
            delete from grunnlag_fradrag where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    private fun Row.toFradragsgrunnlag(): Grunnlag.Fradragsgrunnlag {
        return Grunnlag.Fradragsgrunnlag.tryCreate(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            fradrag = PersistertFradrag(
                fradragskategori = Fradragskategori.valueOf(string("fradragstype")),
                spesifisertKategori = stringOrNull("spesifiserttype"),
                månedsbeløp = double("månedsbeløp"),
                utenlandskInntekt = stringOrNull("utenlandskInntekt")?.let { deserialize(it) },
                periode = Periode.create(fraOgMed = localDate("fraOgMed"), tilOgMed = localDate("tilOgMed")),
                tilhører = FradragTilhører.valueOf(string("tilhører")),
            ),
        ).orNull()!!
    }

    private fun lagre(fradragsgrunnlag: Grunnlag.Fradragsgrunnlag, behandlingId: UUID, tx: TransactionalSession) {
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
                tilhører,
                spesifisertType
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
                :tilhorer,
                :spesifisertType
            )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to fradragsgrunnlag.id,
                    "opprettet" to fradragsgrunnlag.opprettet,
                    "behandlingId" to behandlingId,
                    "fraOgMed" to fradragsgrunnlag.fradrag.periode.fraOgMed,
                    "tilOgMed" to fradragsgrunnlag.fradrag.periode.tilOgMed,
                    "fradragstype" to fradragsgrunnlag.fradrag.fradragskategoriWrapper.kategori.toString(),
                    "manedsbelop" to fradragsgrunnlag.fradrag.månedsbeløp,
                    "utenlandskInntekt" to objectMapper.writeValueAsString(fradragsgrunnlag.fradrag.utenlandskInntekt),
                    "tilhorer" to fradragsgrunnlag.fradrag.tilhører,
                    "spesifisertType" to fradragsgrunnlag.fradrag.fradragskategoriWrapper.spesifisertKategori,
                ),
                tx,
            )
    }
}
