package no.nav.su.se.bakover.database.fritekst

import kotliquery.Row
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.oppdatering
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.domain.fritekst.Fritekst
import no.nav.su.se.bakover.domain.fritekst.FritekstRepo
import no.nav.su.se.bakover.domain.fritekst.FritekstType
import java.util.UUID

class FritekstPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : FritekstRepo {

    override fun hentFritekst(referanseId: UUID, type: FritekstType, sessionContext: SessionContext?): Fritekst? =
        dbMetrics.timeQuery("hentFritekst") {
            sessionFactory.withSession(sessionContext) { session ->
                """
                    select * from fritekst where referanse_id = :referanse_id  and type = :type
                """.trimIndent().hent(
                    mapOf(
                        "referanse_id" to referanseId,
                        "type" to type.name,
                    ),
                    session,
                ) {
                    rowToFritekst(it)
                }
            }
        }

    private fun rowToFritekst(row: Row) = Fritekst(
        referanseId = row.uuid("referanse_id"),
        type = FritekstType.valueOf(row.string("type")),
        fritekst = row.string("fritekst"),
    )

    override fun lagreFritekst(fritekst: Fritekst) {
        dbMetrics.timeQuery("lagreFritekst") {
            sessionFactory.withSession { session ->
                """
                    insert into fritekst (referanse_id, type, fritekst) values (:referanse_id, :type, :fritekst)
                    on conflict (referanse_id, type) do update set fritekst = :fritekst
                """.trimIndent().insert(
                    mapOf(
                        "referanse_id" to fritekst.referanseId,
                        "type" to fritekst.type.name,
                        "fritekst" to fritekst.fritekst,
                    ),
                    session,
                )
            }
        }
    }

    override fun slettFritekst(referanseId: UUID, type: FritekstType) {
        dbMetrics.timeQuery("slettFritekst") {
            sessionFactory.withSession { session ->
                """
                    delete from fritekst where referanse_id = :referanse_id and type = :type
                """.trimIndent().oppdatering(
                    mapOf(
                        "referanse_id" to referanseId,
                        "type" to type.name,
                    ),
                    session,
                )
            }
        }
    }
}
