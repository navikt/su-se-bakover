package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import kotliquery.Row
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.Hendelse
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.SakOpprettetHendelse
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.MetadataJson.Companion.toMeta
import java.util.UUID

class HendelsePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) : HendelseRepo {

    /**
     * @param sessionContext Støtter både [SessionContext] (dersom hendelsen er master data/eneste data) og [no.nav.su.se.bakover.common.persistence.TransactionContext] (i tilfellene hendelsen ikke er master data/eneste data).
     */
    fun persisterSakshendelse(
        hendelse: Sakshendelse,
        type: Hendelsestype,
        data: String,
        sessionContext: SessionContext? = null,
    ) {
        persister(
            hendelse = hendelse,
            type = type,
            data = data,
            sakId = hendelse.sakId,
            sessionContext = sessionContext,
        )
    }

    /**
     * @param sessionContext Støtter både [SessionContext] (dersom hendelsen er master data/eneste data) og [no.nav.su.se.bakover.common.persistence.TransactionContext] (i tilfellene hendelsen ikke er master data/eneste data).
     */
    fun persisterHendelse(
        hendelse: Hendelse<*>,
        type: Hendelsestype,
        data: String,
        sessionContext: SessionContext? = null,
    ) {
        persister(
            hendelse = hendelse,
            type = type,
            data = data,
            sakId = (hendelse as? Sakshendelse)?.sakId,
            sessionContext = sessionContext,
        )
    }

    /**
     * @param sessionContext Støtter både [SessionContext] (dersom hendelsen er master data/eneste data) og [no.nav.su.se.bakover.common.persistence.TransactionContext] (i tilfellene hendelsen ikke er master data/eneste data).
     */
    private fun persister(
        hendelse: Hendelse<*>,
        type: Hendelsestype,
        data: String,
        sakId: UUID?,
        sessionContext: SessionContext? = null,
    ) {
        dbMetrics.timeQuery("persisterHendelse") {
            sessionContext.withOptionalSession(sessionFactory) { session ->
                """
                    insert into hendelse (hendelseId, tidligereHendelseId, sakId, type, data, meta, hendelsestidspunkt, entitetId, versjon)
                    values(
                        :hendelseId,
                        :tidligereHendelseId,
                        :sakId,
                        :type,
                        to_jsonb(:data::jsonb),
                        to_jsonb(:meta::jsonb),
                        :hendelsestidspunkt,
                        :entitetId,
                        :versjon
                    )
                """.trimIndent().insert(
                    params = mapOf(
                        "hendelseId" to hendelse.hendelseId.value,
                        "tidligereHendelseId" to hendelse.tidligereHendelseId?.value,
                        "sakId" to sakId,
                        "type" to type.toString(),
                        "data" to data,
                        "meta" to hendelse.toMeta(),
                        "hendelsestidspunkt" to hendelse.hendelsestidspunkt,
                        "entitetId" to hendelse.entitetId,
                        "versjon" to hendelse.versjon.value,
                    ),
                    session = session,
                )
            }
        }
    }

    /**
     * Kun ment brukt for hendelser uten sakId (vi sjekker at sakId er null)
     */
    fun hentHendelserForType(
        type: Hendelsestype,
        sessionContext: SessionContext? = null,
        limit: Int = 10,
    ): List<PersistertHendelse> {
        return dbMetrics.timeQuery("hentHendelserForSakIdOgType") {
            sessionContext.withOptionalSession(sessionFactory) { session ->
                """
                    select * from hendelse
                    where sakId is null and type = :type
                    order by versjon
                    limit $limit
                """.trimIndent().hentListe(
                    params = mapOf(
                        "type" to type.toString(),
                    ),
                    session = session,
                ) { toPersistertHendelse(it) }
            }
        }
    }

    fun hentHendelserForSakIdOgType(
        sakId: UUID,
        type: Hendelsestype,
        sessionContext: SessionContext? = null,
    ): List<PersistertHendelse> {
        return dbMetrics.timeQuery("hentHendelserForSakIdOgType") {
            sessionContext.withOptionalSession(sessionFactory) { session ->
                """
                    select * from hendelse
                    where sakId = :sakId and type = :type
                    order by versjon
                """.trimIndent().hentListe(
                    params = mapOf(
                        "sakId" to sakId,
                        "type" to type.toString(),
                    ),
                    session = session,
                ) { toPersistertHendelse(it) }
            }
        }
    }

    fun hentHendelseForHendelseId(
        hendelseId: HendelseId,
        sessionContext: SessionContext = sessionFactory.newSessionContext(),
    ): PersistertHendelse? {
        return dbMetrics.timeQuery("hentHendelseForHendelseId") {
            sessionContext.withSession { session ->
                """
                    select * from hendelse
                    where hendelseId = :hendelseId
                """.trimIndent().hent(
                    params = mapOf(
                        "hendelseId" to hendelseId,
                    ),
                    session = session,
                ) { toPersistertHendelse(it) }
            }
        }
    }

    // TODO jah: Flytt til database/sak sitt repo sammen med mapping til/fra.
    @Suppress("unused")
    fun hentSakOpprettetHendelse(
        sakId: UUID,
    ): SakOpprettetHendelse {
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
                    SakOpprettetHendelseJson.toDomain(
                        hendelseId = HendelseId.fromUUID(it.uuid("hendelseId")),
                        sakId = it.uuid("sakId"),
                        metadata = MetadataJson.toDomain(it.string("meta")),
                        json = it.string("data"),
                        entitetId = it.uuid("entitetId"),
                        versjon = it.long("versjon"),
                        hendelsestidspunkt = it.tidspunkt("hendelsestidspunkt"),

                    )
                }.single()
            }
        }
    }

    override fun hentSisteVersjonFraEntitetId(entitetId: UUID, sessionContext: SessionContext?): Hendelsesversjon? {
        return dbMetrics.timeQuery("hentSisteVersjonForEntitetId") {
            sessionFactory.withSessionContext(sessionContext) { sessionContext ->
                sessionContext.withSession { session ->
                    """
                    select max(versjon) from hendelse where entitetId = :entitetId
                    """.trimIndent().hent(
                        params = mapOf(
                            "entitetId" to entitetId,
                        ),
                        session = session,
                    ) {
                        it.longOrNull("max")?.let { Hendelsesversjon(it) }
                    }
                }
            }
        }
    }

    companion object {
        fun toPersistertHendelse(it: Row) = PersistertHendelse(
            data = it.string("data"),
            hendelsestidspunkt = it.tidspunkt("hendelsestidspunkt"),
            versjon = Hendelsesversjon(it.long("versjon")),
            type = Hendelsestype(it.string("type")),
            sakId = it.uuidOrNull("sakId"),
            hendelseId = HendelseId.fromUUID(it.uuid("hendelseId")),
            tidligereHendelseId = it.uuidOrNull("tidligereHendelseId")?.let { HendelseId.fromUUID(it) },
            hendelseMetadata = MetadataJson.toDomain(it.string("meta")),
            entitetId = it.uuid("entitetId"),
        )
    }
}
