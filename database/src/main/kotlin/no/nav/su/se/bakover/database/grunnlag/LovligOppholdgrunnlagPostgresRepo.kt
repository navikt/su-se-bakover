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
import no.nav.su.se.bakover.domain.grunnlag.LovligOppholdGrunnlag
import java.util.UUID

internal class LovligOppholdgrunnlagPostgresRepo(
    private val dbMetrics: DbMetrics,
) {

    internal fun lagre(behandlingId: UUID, grunnlag: List<LovligOppholdGrunnlag>, tx: TransactionalSession) {
        dbMetrics.timeQuery("lagreLovligOppholdgrunnlag") {
            slettForBehandlingId(behandlingId, tx)
            grunnlag.forEach {
                lagre(it, behandlingId, tx)
            }
        }
    }

    internal fun hent(id: UUID, session: Session): LovligOppholdGrunnlag? {
        return dbMetrics.timeQuery("hentLovligOppholdsgrunnlag") {
            """select * from grunnlag_lovligopphold where id=:id""".trimIndent()
                .hent(
                    mapOf(
                        "id" to id,
                    ),
                    session,
                ) {
                    it.toLovligOppholdgrunnlag()
                }
        }
    }

    private fun slettForBehandlingId(behandlingId: UUID, tx: TransactionalSession) {
        """
            delete from grunnlag_lovligopphold where behandlingId = :behandlingId
        """.trimIndent()
            .oppdatering(
                mapOf(
                    "behandlingId" to behandlingId,
                ),
                tx,
            )
    }

    private fun Row.toLovligOppholdgrunnlag(): LovligOppholdGrunnlag {
        return LovligOppholdGrunnlag.tryCreate(
            id = uuid("id"),
            opprettet = tidspunkt("opprettet"),
            periode = Periode.create(localDate("fraOgMed"), localDate("tilOgMed")),
        )
    }

    private fun lagre(grunnlag: LovligOppholdGrunnlag, behandlingId: UUID, tx: TransactionalSession) {
        """
            insert into grunnlag_lovligopphold
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
