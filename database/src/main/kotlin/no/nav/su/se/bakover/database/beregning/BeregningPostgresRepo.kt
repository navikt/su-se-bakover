package no.nav.su.se.bakover.database.beregning

import no.nav.su.se.bakover.common.objectMapper
import no.nav.su.se.bakover.database.beregning.BeregningPostgresRepoInternal.hentBeregningForBehandling
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.withSession
import no.nav.su.se.bakover.domain.beregning.Beregning
import no.nav.su.se.bakover.domain.beregning.fradrag.Fradrag
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
        beregning.fradrag().forEach { opprettFradrag(beregning.id(), it) }
        return hentBeregningForBehandling(behandlingId)!!
    }

    override fun hentBeregningForBehandling(behandlingId: UUID): Beregning? =
        dataSource.withSession { hentBeregningForBehandling(behandlingId, it) }

    override fun slettBeregningForBehandling(behandlingId: UUID) {
        dataSource.withSession { session ->
            "delete from beregning where behandlingId=:id".oppdatering(mapOf("id" to behandlingId), session)
        }
    }

    private fun opprettFradrag(beregningId: UUID, fradrag: Fradrag) {
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
                        "fradragstype" to fradrag.getFradragstype().toString(),
                        "belop" to fradrag.getTotaltFradrag(),
                        "utenlandskInntekt" to objectMapper.writeValueAsString(fradrag.getUtenlandskInntekt())
                    ),
                    session
                )
        }
    }
}
