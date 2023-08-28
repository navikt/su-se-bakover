package no.nav.su.se.bakover.hendelse.infrastructure.persistence

import arrow.core.Nel
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.HendelsekonsumenterRepo
import no.nav.su.se.bakover.hendelse.domain.HendelseskonsumentId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import org.slf4j.LoggerFactory
import java.util.UUID

class HendelsekonsumenterPostgresRepo(
    val sessionFactory: PostgresSessionFactory,
) : HendelsekonsumenterRepo {
    private val log = LoggerFactory.getLogger(this::class.java)

    override fun lagre(
        hendelser: List<HendelseId>,
        konsumentId: HendelseskonsumentId,
        context: SessionContext,
    ) {
        hendelser.forEach {
            lagre(it, konsumentId, context)
        }
    }

    override fun lagre(
        hendelseId: HendelseId,
        konsumentId: HendelseskonsumentId,
        context: SessionContext,
    ) {
        context.withSession {
            """
            INSERT INTO
                hendelse_konsument
                    (id, hendelseId, konsumentId)
                    values
                        (:id, :hendelseId, :konsumentId)
            """.trimIndent().insert(
                mapOf(
                    "id" to UUID.randomUUID(),
                    "hendelseId" to hendelseId.value,
                    "konsumentId" to konsumentId.value,
                ),
                it,
            )
        }
    }

    override fun hentUteståendeSakOgHendelsesIderForKonsumentOgType(
        konsumentId: HendelseskonsumentId,
        hendelsestype: Hendelsestype,
        sx: SessionContext?,
        limit: Int,
    ): Map<UUID, Nel<HendelseId>> {
        return (sx ?: sessionFactory.newSessionContext()).withSession {
            """
            SELECT
                h.sakId, h.hendelseId
            FROM
                hendelse h
            LEFT JOIN hendelse_konsument hk
                ON h.hendelseId = hk.hendelseId AND hk.konsumentId = :konsumentId
            WHERE
                hk.hendelseId IS NULL
                AND h.type = :type
            LIMIT :limit
            """.trimIndent()
                .hentListe(mapOf("type" to hendelsestype.value, "konsumentId" to konsumentId.value, "limit" to limit), it) {
                    it.uuid("sakId") to HendelseId.fromUUID(it.uuid("hendelseId"))
                }.let {
                    it.groupBy { it.first }
                        .mapValues { (_, value) ->
                            value.map { it.second }.toNonEmptyList()
                        }
                }
        }
    }

    override fun hentHendelseIderForKonsumentOgType(
        konsumentId: HendelseskonsumentId,
        hendelsestype: Hendelsestype,
        sx: SessionContext?,
        limit: Int,
    ): List<HendelseId> {
        return (sx ?: sessionFactory.newSessionContext()).withSession {
            """
            SELECT DISTINCT
                h.hendelseId
            FROM
                hendelse h
            LEFT JOIN hendelse_konsument hk
                ON h.hendelseId = hk.hendelseId AND hk.konsumentId = :konsumentId
            WHERE
                hk.hendelseId IS NULL
                AND h.type = :type
            LIMIT :limit
            """.trimIndent()
                .hentListe(mapOf("type" to hendelsestype, "konsumentId" to konsumentId, "limit" to limit), it) {
                    HendelseId.fromUUID(it.uuid("hendelseId"))
                }
        }
    }
}
