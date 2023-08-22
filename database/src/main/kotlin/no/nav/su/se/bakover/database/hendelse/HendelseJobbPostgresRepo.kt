package no.nav.su.se.bakover.database.hendelse

import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.infrastructure.persistence.hentListe
import no.nav.su.se.bakover.common.infrastructure.persistence.insert
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.oppgave.domain.HendelseJobbRepo
import java.util.UUID

class HendelseJobbPostgresRepo(
    val sessionFactory: PostgresSessionFactory,
) : HendelseJobbRepo {
    override fun lagre(hendelser: List<HendelseId>, jobbNavn: String, context: SessionContext) {
        hendelser.forEach {
            lagre(it, jobbNavn, context)
        }
    }

    override fun lagre(hendelseId: HendelseId, jobbNavn: String, context: SessionContext) {
        context.withSession {
            """
            INSERT INTO
                hendelse_jobb 
                    (id, hendelseId, jobbNavn)
                    values
                        (:id, :hendelseId, :jobbNavn)
            """.trimIndent().insert(
                mapOf("id" to UUID.randomUUID(), "hendelseId" to hendelseId.value, "jobbNavn" to jobbNavn),
                it,
            )
        }
    }

    override fun hentSakIdOgHendelseIderForNavnOgType(
        jobbNavn: String,
        hendelsestype: String,
        sx: SessionContext?,
        limit: Int,
    ): Map<UUID, List<HendelseId>> {
        return (sx ?: sessionFactory.newSessionContext()).withSession {
            """
           select 
                h.sakId, h.hendelseId 
           from 
                hendelse h 
                    left join hendelse_jobb hj 
                        on h.hendelseId = hj.hendelseId 
           where
             hj.jobbNavn = :jobbNavn
             and h.type IN (:type)
             limit :limit
            """.trimIndent().hentListe(mapOf("type" to hendelsestype, "jobbNavn" to jobbNavn, "limit" to limit), it) {
                it.uuid("sakId") to HendelseId.fromUUID(it.uuid("hendelseId"))
            }.let {
                it.groupBy { it.first }
                    .mapValues { (_, value) ->
                        value.map { it.second }
                    }
            }
        }
    }
}
