package no.nav.su.se.bakover.database.regulering

import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.database.regulering.ReguleringssupplementForJson.Companion.toDbJson
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
                INSERT INTO reguleringssupplement (id, opprettet, supplement) values (:id, :opprettet, to_jsonb(:supplement::jsonb))
                """.trimIndent().insert(mapOf("id" to supplement.id, "opprettet" to supplement.opprettet, "supplement" to serialize(supplement.toDbJson())), it)
            }
        }
    }
}
