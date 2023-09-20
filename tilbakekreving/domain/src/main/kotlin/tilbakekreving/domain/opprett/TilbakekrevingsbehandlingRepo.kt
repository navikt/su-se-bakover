package tilbakekreving.domain.opprett

import no.nav.su.se.bakover.common.persistence.SessionContext

interface TilbakekrevingsbehandlingRepo {
    fun opprett(
        hendelse: OpprettetTilbakekrevingsbehandlingHendelse,
        sessionContext: SessionContext? = null,
    )
}
