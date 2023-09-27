package tilbakekreving.domain.opprett

import no.nav.su.se.bakover.common.persistence.SessionContext
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelser
import java.util.UUID

interface TilbakekrevingsbehandlingRepo {
    fun lagre(
        hendelse: TilbakekrevingsbehandlingHendelse,
        sessionContext: SessionContext? = null,
    )

    fun hentForSak(sakId: UUID, sessionContext: SessionContext? = null): TilbakekrevingsbehandlingHendelser
}
