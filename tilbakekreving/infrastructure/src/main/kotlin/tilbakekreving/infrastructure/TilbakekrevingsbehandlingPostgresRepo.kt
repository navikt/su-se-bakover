package tilbakekreving.infrastructure

import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.PersistertHendelse
import tilbakekreving.domain.opprett.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo
import java.util.UUID

private val OpprettTilbakekrevingsbehandlingHendelsestype = Hendelsestype("OPPRETT_TILBAKEKREVINGSBEHANDLING")

class TilbakekrevingsbehandlingPostgresRepo(
    private val sessionFactory: PostgresSessionFactory,
    private val hendelseRepo: HendelseRepo,
) : TilbakekrevingsbehandlingRepo {
    override fun opprett(
        hendelse: OpprettetTilbakekrevingsbehandlingHendelse,
        sessionContext: SessionContext?,
    ) {
        sessionContext.withOptionalSession(sessionFactory) {
            // TODO jah: Kanskje vi bør flytte denne til interfacet?
            (hendelseRepo as HendelsePostgresRepo).persisterSakshendelse(
                hendelse = hendelse,
                type = OpprettTilbakekrevingsbehandlingHendelsestype,
                data = hendelse.toJson(),
                sessionContext = null,
            )
        }
    }

    override fun hentForSak(sakId: UUID, sessionContext: SessionContext?): List<OpprettetTilbakekrevingsbehandlingHendelse> {
        return (hendelseRepo as HendelsePostgresRepo)
            .hentHendelserForSakIdOgType(
                sakId = sakId,
                type = OpprettTilbakekrevingsbehandlingHendelsestype,
                sessionContext = sessionContext ?: sessionFactory.newSessionContext(),
            )
            .toOpprettetTilbakekrevingsbehandlingHendelse()
    }
}

private fun List<PersistertHendelse>.toOpprettetTilbakekrevingsbehandlingHendelse(): List<OpprettetTilbakekrevingsbehandlingHendelse> =
    this.map {
        OpprettTilbakekrevingsbehandlingHendelseDbJson.toDomain(
            data = it.data,
            hendelseId = it.hendelseId,
            sakId = it.sakId!!,
            hendelsestidspunkt = it.hendelsestidspunkt,
            versjon = it.versjon,
            meta = it.hendelseMetadata,
        )
    }
