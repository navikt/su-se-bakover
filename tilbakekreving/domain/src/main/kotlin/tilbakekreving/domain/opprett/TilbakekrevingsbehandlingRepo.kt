package tilbakekreving.domain.opprett

import no.nav.su.se.bakover.common.persistence.SessionContext
import java.util.UUID

interface TilbakekrevingsbehandlingRepo {
    fun opprett(
        hendelse: OpprettetTilbakekrevingsbehandlingHendelse,
        sessionContext: SessionContext? = null,
    )

    fun hentForSak(sakId: UUID, sessionContext: SessionContext? = null): List<OpprettetTilbakekrevingsbehandlingHendelse>
}
