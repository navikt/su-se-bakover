package tilbakekreving.domain.opprett

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import tilbakekreving.domain.TilbakekrevingbehandlingsSerie
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelse
import tilbakekreving.domain.TilbakekrevingsbehandlingHendelser
import java.util.UUID

interface TilbakekrevingsbehandlingRepo {
    fun lagre(
        hendelse: TilbakekrevingsbehandlingHendelse,
        sessionContext: SessionContext? = null,
    )

    fun hentHendelse(
        id: HendelseId,
        sessionContext: SessionContext? = null,
    ): TilbakekrevingsbehandlingHendelse?

    fun hentForSak(sakId: UUID, sessionContext: SessionContext? = null): TilbakekrevingsbehandlingHendelser

    fun hentBehandlingsSerieFor(hendelse: TilbakekrevingsbehandlingHendelse, sessionContext: SessionContext? = null): TilbakekrevingbehandlingsSerie
}
