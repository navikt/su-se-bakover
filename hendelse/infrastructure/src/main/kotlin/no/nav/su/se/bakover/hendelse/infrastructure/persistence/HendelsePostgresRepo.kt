package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import no.nav.su.se.bakover.common.deserialize
import no.nav.su.se.bakover.common.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.hentListe
import no.nav.su.se.bakover.common.persistence.insert
import no.nav.su.se.bakover.common.persistence.tidspunkt
import no.nav.su.se.bakover.hendelse.domain.Hendelse
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.hendelse.domain.SakOpprettetHendelse
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.MetadataJson.Companion.toMeta
import java.util.UUID

// TODO jah: Flytt til database/sak sitt repo sammen med mapping til/fra.
class HendelsePostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val dbMetrics: DbMetrics,
) {
    /**
     * @param sessionContext Støtter både [SessionContext] (dersom hendelsen er master data/eneste data) og [no.nav.su.se.bakover.common.persistence.TransactionContext] (i tilfellene hendelsen ikke er master data/eneste data).
     */
    fun persister(
        hendelse: Hendelse,
        type: String,
        data: String,
        sessionContext: SessionContext = sessionFactory.newSessionContext(),
    ) {
        dbMetrics.timeQuery("persisterHendelse") {
            sessionContext.withSession { session ->
                """
                    insert into hendelse (hendelseId, sakId, type, data, meta, hendelsestidspunkt, entitetId, versjon)
                    values(
                        :hendelseId,
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
                        "hendelseId" to hendelse.hendelseId,
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

    fun hentHendelserForSakIdOgType(
        sakId: UUID,
        type: String,
    ): List<PersistertHendelse> {
        return dbMetrics.timeQuery("hentHendelserForSakIdOgType") {
            sessionFactory.withSession { session ->
                """
                    select * from hendelse
                    where sakId = :sakId and type = :type
                    order by versjon
                """.trimIndent().hentListe(
                    params = mapOf(
                        "sakId" to sakId,
                        "type" to type,
                    ),
                    session = session,
                ) {
                    PersistertHendelse(
                        data = it.string("data"),
                        hendelsestidspunkt = it.tidspunkt("hendelsestidspunkt"),
                        versjon = Hendelsesversjon(it.long("versjon")),
                    )
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
                    select * from hendelse where sakId = :sakId
                """.trimIndent().hentListe(
                    params = mapOf(
                        "sakId" to sakId,
                    ),
                    session = session,
                ) {
                    SakOpprettetHendelseJson.toDomain(
                        hendelseId = it.uuid("hendelseId"),
                        sakId = it.uuid("sakId"),
                        metadata = deserialize<MetadataJson>(it.string("meta")).toDomain(),
                        json = it.string("data"),
                        entitetId = it.uuid("entitetId"),
                        versjon = it.long("versjon"),
                        hendelsestidspunkt = it.tidspunkt("hendelsestidspunkt"),

                    )
                }.single()
            }
        }
    }
}
