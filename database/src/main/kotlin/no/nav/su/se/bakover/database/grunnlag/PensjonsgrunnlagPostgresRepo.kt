package no.nav.su.se.bakover.database.grunnlag

import kotliquery.Row
import no.nav.su.se.bakover.common.periode.Periode
import no.nav.su.se.bakover.database.DbMetrics
import no.nav.su.se.bakover.database.Session
import no.nav.su.se.bakover.database.TransactionalSession
import no.nav.su.se.bakover.database.hent
import no.nav.su.se.bakover.database.insert
import no.nav.su.se.bakover.database.oppdatering
import no.nav.su.se.bakover.database.tidspunkt
import no.nav.su.se.bakover.domain.grunnlag.Pensjonsgrunnlag
import java.util.UUID

internal class PensjonsgrunnlagPostgresRepo(
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(behandlingId: UUID, grunnlag: List<Pensjonsgrunnlag>, tx: TransactionalSession) {
        dbMetrics.timeQuery("lagrePensjonsgrunnlag") {
            slettForBehandlingId(behandlingId, tx)
            grunnlag.forEach {
                lagre(it, behandlingId, tx)
            }
        }
    }

    internal fun hent(id: UUID, session: Session): Pensjonsgrunnlag? {
        return dbMetrics.timeQuery("hentPensjonsgrunnlag") {
            """select * from grunnlag_pensjon where id=:id""".trimIndent()
                .hent(
                    mapOf(
                        "id" to id,
                    ),
                    session,
                ) {
                    it.toPensjonsgrunnlag()
                }
        }
    }

    private fun slettForBehandlingId(behandlingId: UUID, tx: TransactionalSession) {
        """
            delete from grunnlag_pensjon where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    private fun Row.toPensjonsgrunnlag(): Pensjonsgrunnlag {
        return Pensjonsgrunnlag(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            periode = Periode.create(localDate("fraOgMed"), localDate("tilOgMed")),
        )
    }

    private fun lagre(grunnlag: Pensjonsgrunnlag, behandlingId: UUID, tx: TransactionalSession) {
        """
            insert into grunnlag_pensjon
            (
                id,
                opprettet,
                behandlingId,
                fraOgMed,
                tilOgMed
            ) values 
            (
                :id,
                :opprettet,
                :behandlingId,
                :fraOgMed,
                :tilOgMed
            )
        """.trimIndent()
            .insert(
                mapOf(
                    "id" to grunnlag.id,
                    "opprettet" to grunnlag.opprettet,
                    "behandlingId" to behandlingId,
                    "fraOgMed" to grunnlag.periode.fraOgMed,
                    "tilOgMed" to grunnlag.periode.tilOgMed,
                ),
                tx,
            )
    }
}
