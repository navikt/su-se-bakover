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
        meta: String,
        sessionContext: SessionContext? = null,
    ) {
        persister(
            hendelse = hendelse,
            type = type,
            data = data,
            sakId = hendelse.sakId,
            sessionContext = sessionContext,
            meta = meta,
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
        meta: String,
    ) {
        persister(
            hendelse = hendelse,
            type = type,
            data = data,
            sakId = (hendelse as? Sakshendelse)?.sakId,
            sessionContext = sessionContext,
            meta = meta,
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
        meta: String,
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
                        "meta" to meta,
                        "hendelsestidspunkt" to hendelse.hendelsestidspunkt,
                        "entitetId" to hendelse.entitetId,
                        "versjon" to hendelse.versjon.value,
                    ),
                    session = session,
                )
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
                    select hendelseId, data, hendelsestidspunkt, versjon, type, sakId, tidligereHendelseId, entitetId
                    from hendelse
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
        sessionContext: SessionContext? = sessionFactory.newSessionContext(),
    ): PersistertHendelse? {
        return dbMetrics.timeQuery("hentHendelseForHendelseId") {
            sessionFactory.withSessionContext(sessionContext) { context ->
                context.withSession { session ->
                    // Henter ikke metadata i disse tilfellene, da det ikke trengs. Ekskluderer også et par andre felter.
                    """
                    select hendelseId, data, hendelsestidspunkt, versjon, type, sakId, tidligereHendelseId, entitetId
                    from hendelse
                    where hendelseId = :hendelseId
                    """.trimIndent().hent(
                        params = mapOf(
                            "hendelseId" to hendelseId.value,
                        ),
                        session = session,
                    ) { toPersistertHendelse(it) }
                }
            }
        }
    }

    fun hentHendelseMedMetadataForHendelseId(
        hendelseId: HendelseId,
        sessionContext: SessionContext? = sessionFactory.newSessionContext(),
    ): PersistertHendelseMedMetadata? {
        return dbMetrics.timeQuery("hentHendelseMedMetadataForHendelseId") {
            sessionFactory.withSessionContext(sessionContext) { context ->
                context.withSession { session ->
                    // Det er noen få felter vi ikke trenger.
                    """
                    select hendelseId, data, hendelsestidspunkt, versjon, type, sakId, tidligereHendelseId, entitetId, meta
                    from hendelse
                    where hendelseId = :hendelseId
                    """.trimIndent().hent(
                        params = mapOf(
                            "hendelseId" to hendelseId.value,
                        ),
                        session = session,
                    ) { PersistertHendelseMedMetadata(toPersistertHendelse(it), it.string("meta")) }
                }
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
                    select hendelseId, data, hendelsestidspunkt, versjon, entitetId
                    from hendelse
                    where sakId = :sakId
                """.trimIndent().hentListe(
                    params = mapOf(
                        "sakId" to sakId,
                    ),
                    session = session,
                ) {
                    SakOpprettetHendelseJson.toDomain(
                        hendelseId = HendelseId.fromUUID(it.uuid("hendelseId")),
                        sakId = sakId,
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
                    select max(versjon)
                    from hendelse
                    where entitetId = :entitetId
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
            entitetId = it.uuid("entitetId"),
        )
    }
}
