package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.hentListe
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.hendelse.application.Hendelse
import no.nav.su.se.bakover.hendelse.application.HendelseRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelseType.Companion.toDatabaseType
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.MetadataJson.Companion.toMeta
import java.util.UUID

class HendelsePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : HendelseRepo {
    /**
     * @param sessionContext Støtter både [SessionContext] (dersom hendelsen er master data/eneste data) og [no.nav.su.se.bakover.common.persistence.TransactionContext] (i tilfellene hendelsen ikke er master data/eneste data).
     */
    override fun persisterHendelse(
        hendelse: Hendelse,
        sessionContext: SessionContext,
    ) {
        dbMetrics.timeQuery("persisterHendelse") {
            sessionContext.withSession { session ->
                """
                    insert into hendelse (id, sakId, type, data, meta, entityId, version)
                    values(
                        :id,
                        :sakId,
                        :type,
                        to_jsonb(:data::jsonb),
                        to_jsonb(:meta::jsonb),
                        :entityId,
                        :version
                        
                    )
                """.trimIndent().insert(
                    params = mapOf(
                        "id" to hendelse.id,
                        "sakId" to hendelse.sakId,
                        "type" to hendelse.toDatabaseType(),
                        "data" to hendelse.toData(),
                        "meta" to hendelse.toMeta(),
                        "entityId" to hendelse.entitetId,
                        "version" to hendelse.versjon.value,
                    ),
                    session = session,
                )
            }
        }
    }

    override fun hentHendelserForSak(sakId: UUID): List<Hendelse> {
        return dbMetrics.timeQuery("hentHendelserForSak") {
            sessionFactory.withSession { session ->
                """
                    select * from hendelse where sakId = :sakId
                """.trimIndent().hentListe(
                    params = mapOf(
                        "sakId" to sakId,
                    ),
                    session = session,
                ) {
                    toHendelse(
                        type = it.string("type"),
                        dataJson = it.string("data"),
                        metadataJson = it.string("meta"),
                        entitetId = it.uuid("entityId"),
                        versjon = it.long("version"),
                    )
                }
            }
        }
    }
}
