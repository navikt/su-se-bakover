package no.nav.su.se.bakover.oppgave.infrastructure

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.hent
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.common.persistence.SessionFactory
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseMetadata
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import java.util.UUID

val OppgaveHendelsestype = Hendelsestype("OPPGAVE")

class OppgaveHendelsePostgresRepo(
    private val dbMetrics: DbMetrics,
    private val hendelseRepo: HendelsePostgresRepo,
    private val sessionFactory: SessionFactory,
) : OppgaveHendelseRepo {

    override fun lagre(
        hendelse: OppgaveHendelse,
        meta: OppgaveHendelseMetadata,
        sessionContext: SessionContext,
    ) {
        dbMetrics.timeQuery("lagreOppgaveHendelse") {
            hendelseRepo.persisterSakshendelse(
                hendelse = hendelse,
                meta = meta.toDbJson(),
                type = OppgaveHendelsestype,
                data = hendelse.toDbJson(),
                sessionContext = sessionContext,
            )
        }
    }

    override fun hentForSak(sakId: UUID, sessionContext: SessionContext?): List<OppgaveHendelse> {
        return dbMetrics.timeQuery("hentOppgaveHendelserForSak") {
            hendelseRepo.hentHendelserForSakIdOgType(sakId, OppgaveHendelsestype, sessionContext).map {
                it.toOppgaveHendelse()
            }
        }
    }

    override fun hentHendelseForRelatert(
        relatertHendelseId: HendelseId,
        sakId: UUID,
        sessionContext: SessionContext?,
    ): OppgaveHendelse? {
        return dbMetrics.timeQuery("hentHendelseForRelatert") {
            sessionContext.withOptionalSession(sessionFactory) {
                """
                SELECT data,hendelsestidspunkt,versjon,type,sakId,hendelseId,tidligereHendelseId,entitetId
                FROM hendelse
                WHERE
                    type = 'OPPGAVE' AND 
                    sakId = :sakId AND
                    :relatertHendelseId = ANY (ARRAY(SELECT * from jsonb_array_elements_text(data->'relaterteHendelser'))::text[])
                """.trimIndent().hent(mapOf("relatertHendelseId" to relatertHendelseId.toString(), "sakId" to sakId), it) {
                    HendelsePostgresRepo.toPersistertHendelse(it)
                }?.toOppgaveHendelse()
            }
        }
    }
}
