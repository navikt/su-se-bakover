package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseActionRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import java.util.UUID

class HendelseActionPostgresRepo(
    val sessionFactory: PostgresSessionFactory,
) : HendelseActionRepo {
    override fun lagre(hendelser: List<HendelseId>, action: String, context: SessionContext) {
        hendelser.forEach {
            lagre(it, action, context)
        }
    }

    override fun lagre(hendelseId: HendelseId, action: String, context: SessionContext) {
        context.withSession {
            """
            INSERT INTO
                hendelse_action 
                    (id, hendelseId, action)
                    values
                        (:id, :hendelseId, :action)
            """.trimIndent().insert(
                mapOf("id" to UUID.randomUUID(), "hendelseId" to hendelseId.value, "action" to action),
                it,
            )
        }
    }

    override fun hentSakOgHendelsesIderSomIkkeHarKjørtAction(
        action: String,
        hendelsestype: String,
        sx: SessionContext?,
        limit: Int,
    ): Map<UUID, List<HendelseId>> {
        return (sx ?: sessionFactory.newSessionContext()).withSession {
            """
            SELECT
                h.sakId, h.hendelseId
            FROM
                hendelse h
            LEFT JOIN hendelse_action ha
                ON h.hendelseId = ha.hendelseId AND ha.action = :action
            WHERE
                ha.hendelseId IS NULL
                AND h.type = :type
            LIMIT :limit
            """.trimIndent().hentListe(mapOf("type" to hendelsestype, "action" to action, "limit" to limit), it) {
                it.uuid("sakId") to HendelseId.fromUUID(it.uuid("hendelseId"))
            }.let {
                it.groupBy { it.first }
                    .mapValues { (_, value) ->
                        value.map { it.second }
                    }
            }
        }
    }

    override fun hentHendelseIderForActionOgType(
        action: String,
        hendelsestype: String,
        sx: SessionContext?,
        limit: Int,
    ): List<HendelseId> {
        return (sx ?: sessionFactory.newSessionContext()).withSession {
            """
            SELECT DISTINCT
                h.hendelseId
            FROM
                hendelse h
            LEFT JOIN hendelse_action ha
                ON h.hendelseId = ha.hendelseId AND ha.action = :action
            WHERE
                ha.hendelseId IS NULL
                AND h.type = :type
            LIMIT :limit
            """.trimIndent().hentListe(mapOf("type" to hendelsestype, "action" to action, "limit" to limit), it) {
                HendelseId.fromUUID(it.uuid("hendelseId"))
            }
        }
    }
}
