package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import arrow.core.NonEmptyList
import kotliquery.Row
import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.infrastructure.persistence.tidspunkt
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
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
    fun persister(
        hendelse: Sakshendelse,
        type: String,
        data: String,
        sessionContext: SessionContext = sessionFactory.newSessionContext(),
    ) {
        dbMetrics.timeQuery("persisterHendelse") {
            sessionContext.withSession { session ->
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
                        "sakId" to hendelse.sakId,
                        "type" to type,
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

    fun hentSisteHendelseforSakIdOgTyper(
        sakId: UUID,
        typer: NonEmptyList<String>,
    ): PersistertHendelse? {
        return dbMetrics.timeQuery("hentSisteHendelseforSakIdOgTyper") {
            sessionFactory.withSession { session ->
                """
                    select * from hendelse
                    where sakId = :sakId and type IN (:type)
                    order by versjon desc
                    limit 1
                """.trimIndent().hent(
                    params = mapOf(
                        "sakId" to sakId,
                        "type" to typer.joinToString { "," },
                    ),
                    session = session,
                ) {
                    toPersistertHendelse(it)
                }
            }
        }
    }

    fun hentHendelserForSakIdOgTyper(
        sakId: UUID,
        typer: NonEmptyList<String>,
        sessionContext: SessionContext = sessionFactory.newSessionContext(),
    ): List<PersistertHendelse> {
        return dbMetrics.timeQuery("hentHendelserForSakIdOgTyper") {
            sessionContext.withSession { session ->
                """
                    select * from hendelse
                    where sakId = :sakId and type IN (${typer.joinToString { "'$it'" }})
                    order by versjon
                """.trimIndent().hentListe(
                    params = mapOf(
                        "sakId" to sakId,
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

    override fun hentSisteVersjonFraEntitetId(entitetId: UUID, sessionContext: SessionContext): Hendelsesversjon? {
        return dbMetrics.timeQuery("hentSisteVersjonForEntitetId") {
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

    override fun defaultSessionContext(): SessionContext {
        return sessionFactory.newSessionContext()
    }

    companion object {
        fun toPersistertHendelse(it: Row) = PersistertHendelse(
            data = it.string("data"),
            hendelsestidspunkt = it.tidspunkt("hendelsestidspunkt"),
            versjon = Hendelsesversjon(it.long("versjon")),
            type = it.string("type"),
            sakId = it.uuidOrNull("sakId"),
            hendelseId = it.uuid("hendelseId"),
            tidligereHendelseId = it.uuidOrNull("tidligereHendelseId"),
            hendelseMetadata = MetadataJson.toDomain(it.string("meta")),
            enitetId = it.uuid("entitetId"),
        )
    }
}
