package no.nav.su.se.bakover.oppgave.infrastructure

import no.nav.su.se.bakover.common.infrastructure.persistence.DbMetrics
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelse
import no.nav.su.se.bakover.oppgave.domain.OppgaveHendelseRepo
import no.nav.su.se.bakover.oppgave.infrastructure.OppgaveHendelseData.Companion.toStringifiedJson
import java.util.UUID

val OppgaveHendelsestype = Hendelsestype("OPPGAVE")

class OppgaveHendelsePostgresRepo(
    private val dbMetrics: DbMetrics,
    private val hendelseRepo: HendelsePostgresRepo,
) : OppgaveHendelseRepo {

    override fun lagre(hendelse: OppgaveHendelse, sessionContext: SessionContext) {
        dbMetrics.timeQuery("lagreOppgaveHendelse") {
            hendelseRepo.persisterSakshendelse(
                hendelse = hendelse,
                type = OppgaveHendelsestype,
                data = hendelse.toStringifiedJson(),
                sessionContext = sessionContext,
            )
        }
    }

    override fun hentForSak(sakId: UUID): List<OppgaveHendelse> {
        return dbMetrics.timeQuery("hentOppgaveHendelserForSak") {
            hendelseRepo.hentHendelserForSakIdOgType(sakId, OppgaveHendelsestype).map {
                it.toOppgaveHendelse(it.hendelseMetadata)
            }
        }
    }
}
