package no.nav.su.se.bakover.database.regulering

import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.domain.regulering.supplement.Reguleringssupplement

/**
 * Abstraksjonsklasse for [ReguleringPostgresRepo] som håndterer reguleringssupplement
 */
internal class ReguleringssupplementPostgresRepo(
    val sessionFactory: SessionFactory,
) {
    fun lagre(supplement: Reguleringssupplement) {
        sessionFactory.withSessionContext {
            it.withSession {
                """
                INSERT INTO reguleringssupplement (id, opprettet, supplement, csv) values (:id, :opprettet, to_jsonb(:supplement::jsonb), :csv)
                """.trimIndent().insert(
                    mapOf(
                        "id" to supplement.id,
                        "opprettet" to supplement.opprettet,
                        "supplement" to supplement.toDbJson(),
                        "csv" to supplement.originalCsv,
                    ),
                    it,
                )
            }
        }
    }
}
