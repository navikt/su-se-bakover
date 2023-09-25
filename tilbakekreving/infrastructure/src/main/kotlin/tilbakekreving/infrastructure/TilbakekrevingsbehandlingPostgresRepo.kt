package tilbakekreving.infrastructure

import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionContext.Companion.withOptionalSession
import no.nav.su.se.bakover.common.infrastructure.persistence.PostgresSessionFactory
import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseRepo
import no.nav.su.se.bakover.hendelse.domain.Hendelsestype
import no.nav.su.se.bakover.hendelse.infrastructure.persistence.HendelsePostgresRepo
import tilbakekreving.domain.opprett.OpprettetTilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.opprett.TilbakekrevingsbehandlingRepo

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
                data = "",
                sessionContext = null,

            )
        }
    }
}
