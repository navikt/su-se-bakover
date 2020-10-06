package no.nav.su.se.bakover.database.beregning

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.beregning.BeregningRepoInternal.hentBeregningForBehandling
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.Fradrag
import no.nav.su.se.bakover.domain.beregning.Månedsberegning
import java.util.UUID
import javax.sql.DataSource

internal class BeregningPostgresRepo(
    private val dataSource: DataSource
) : BeregningRepo {

    override fun opprettBeregningForBehandling(behandlingId: UUID, beregning: Beregning): Beregning {
        slettBeregningForBehandling(behandlingId)
        dataSource.withSession { session ->
            "insert into beregning (id, opprettet, fom, tom, behandlingId, sats) values (:id, :opprettet, :fom, :tom, :behandlingId, :sats)".oppdatering(
                mapOf(
                    "id" to beregning.id,
                    "opprettet" to beregning.opprettet,
                    "fom" to beregning.fraOgMed,
                    "tom" to beregning.tilOgMed,
                    "behandlingId" to behandlingId,
                    "sats" to beregning.sats.name
                ),
                session
            )
        }
        beregning.månedsberegninger.forEach { opprettMånedsberegning(beregning.id, it) }
        beregning.fradrag.forEach { opprettFradrag(beregning.id, it) }
        return hentBeregningForBehandling(behandlingId)!!
    }

    override fun hentBeregningForBehandling(behandlingId: UUID): Beregning? =
        dataSource.withSession { hentBeregningForBehandling(behandlingId, it) }

    override fun slettBeregningForBehandling(behandlingId: UUID) {
        dataSource.withSession { session ->
            "delete from beregning where behandlingId=:id".oppdatering(mapOf("id" to behandlingId), session)
        }
    }

    private fun opprettMånedsberegning(beregningId: UUID, månedsberegning: Månedsberegning) {
        dataSource.withSession { session ->
            """
            insert into månedsberegning (id, opprettet, fom, tom, grunnbeløp, beregningId, sats, beløp, fradrag)
            values (:id, :opprettet, :fom, :tom, :grunnbelop, :beregningId, :sats, :belop, :fradrag)
        """.oppdatering(
                mapOf(
                    "id" to månedsberegning.id,
                    "opprettet" to månedsberegning.opprettet,
                    "fom" to månedsberegning.fraOgMed,
                    "tom" to månedsberegning.tilOgMed,
                    "grunnbelop" to månedsberegning.grunnbeløp,
                    "beregningId" to beregningId,
                    "sats" to månedsberegning.sats.name,
                    "belop" to månedsberegning.beløp,
                    "fradrag" to månedsberegning.fradrag
                ),
                session
            )
        }
    }

    private fun opprettFradrag(beregningId: UUID, fradrag: Fradrag) {
        dataSource.withSession { session ->
            """
            insert into fradrag (id, beregningId, fradragstype, beløp, utenlandskInntekt, inntektDelerAvPeriode)
            values (:id, :beregningId, :fradragstype, :belop, to_json(:utenlandskInntekt::json), to_json(:inntektDelerAvPeriode::json))
        """
                .oppdatering(
                    mapOf(
                        "id" to fradrag.id,
                        "beregningId" to beregningId,
                        "fradragstype" to fradrag.type.toString(),
                        "belop" to fradrag.beløp,
                        "utenlandskInntekt" to objectMapper.writeValueAsString(fradrag.utenlandskInntekt),
                        "inntektDelerAvPeriode" to objectMapper.writeValueAsString(fradrag.inntektDelerAvPeriode)
                    ),
                    session
                )
        }
    }
}
