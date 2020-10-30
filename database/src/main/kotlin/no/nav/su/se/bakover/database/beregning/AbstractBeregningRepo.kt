package no.nav.su.se.bakover.database.beregning

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.beregning.AbstractBeregningRepoInternal.hentBeregningForBehandling
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.beregning.beregning.AbstractBeregning
import no.nav.su.se.bakover.domain.beregning.beregning.AbstractMånedsberegning
import no.nav.su.se.bakover.domain.beregning.fradrag.AbstractFradrag
import java.util.UUID
import javax.sql.DataSource

internal class AbstractBeregningRepo(
    private val dataSource: DataSource
) {

    fun opprettBeregningForBehandling(behandlingId: UUID, beregning: AbstractBeregning): AbstractBeregning {
        slettBeregningForBehandling(behandlingId)
        dataSource.withSession { session ->
            "insert into beregning (id, opprettet, fom, tom, behandlingId, sats) values (:id, :opprettet, :fom, :tom, :behandlingId, :sats)".oppdatering(
                mapOf(
                    "id" to beregning.id(),
                    "opprettet" to beregning.opprettet(),
                    "fom" to beregning.periode().fraOgMed(),
                    "tom" to beregning.periode().tilOgMed(),
                    "behandlingId" to behandlingId,
                    "sats" to beregning.sats().name
                ),
                session
            )
        }
        beregning.månedsberegninger().forEach { opprettMånedsberegning(beregning.id(), it) }
        beregning.fradrag().forEach { opprettFradrag(beregning.id(), it) }
        return hentBeregningForBehandling(behandlingId)!!
    }

    fun hentBeregningForBehandling(behandlingId: UUID): AbstractBeregning? =
        dataSource.withSession { hentBeregningForBehandling(behandlingId, it) }

    fun slettBeregningForBehandling(behandlingId: UUID) {
        dataSource.withSession { session ->
            "delete from beregning where behandlingId=:id".oppdatering(mapOf("id" to behandlingId), session)
        }
    }

    // TODO DO WE EVEN NEED THIS? OK TO RE-COMPUTE WHEN GETTING FROM DB? COULD USE SEPARATE MECHANISM FOR UPDATE/VIEW? NOT RETRIEVED ATM.
    private fun opprettMånedsberegning(beregningId: UUID, månedsberegning: AbstractMånedsberegning) {
        dataSource.withSession { session ->
            """
            insert into månedsberegning (id, opprettet, fom, tom, grunnbeløp, beregningId, sats, beløp, fradrag)
            values (:id, :opprettet, :fom, :tom, :grunnbelop, :beregningId, :sats, :belop, :fradrag)
        """.oppdatering(
                mapOf(
                    "id" to månedsberegning.id(),
                    "opprettet" to månedsberegning.opprettet(),
                    "fom" to månedsberegning.periode().fraOgMed(),
                    "tom" to månedsberegning.periode().tilOgMed(),
                    "grunnbelop" to månedsberegning.grunnbeløp(),
                    "beregningId" to beregningId,
                    "sats" to månedsberegning.sats().name,
                    "belop" to månedsberegning.sum(),
                    "fradrag" to månedsberegning.fradrag()
                ),
                session
            )
        }
    }

    private fun opprettFradrag(beregningId: UUID, fradrag: AbstractFradrag) {
        dataSource.withSession { session ->
            """
            insert into fradrag (id, opprettet, fom, tom,  beregningId, fradragstype, beløp, utenlandskInntekt)
            values (:id, :opprettet, :fom, :tom, :beregningId, :fradragstype, :belop, to_json(:utenlandskInntekt::json))
        """
                .oppdatering(
                    mapOf(
                        "id" to fradrag.id(),
                        "opprettet" to fradrag.opprettet(),
                        "fom" to fradrag.periode().fraOgMed(),
                        "tom" to fradrag.periode().tilOgMed(),
                        "beregningId" to beregningId,
                        "fradragstype" to fradrag.type().toString(),
                        "belop" to fradrag.totalBeløp(),
                        "utenlandskInntekt" to objectMapper.writeValueAsString(fradrag.utenlandskInntekt())
                    ),
                    session
                )
        }
    }
}
