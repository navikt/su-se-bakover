package tilbakekreving.domain

import no.nav.su.se.bakover.common.persistence.SessionContext
import no.nav.su.se.bakover.hendelse.domain.DefaultHendelseMetadata
import no.nav.su.se.bakover.hendelse.domain.HendelseId
import tilbakekreving.domain.iverksettelse.IverksattHendelseMetadata
import java.util.UUID

interface TilbakekrevingsbehandlingRepo {
    fun lagre(
        hendelse: TilbakekrevingsbehandlingHendelse,
        meta: DefaultHendelseMetadata,
        sessionContext: SessionContext? = null,
    )

    fun lagreIverksattTilbakekrevingshendelse(
        hendelse: IverksattHendelse,
        meta: IverksattHendelseMetadata,
        sessionContext: SessionContext? = null,
    )

    fun hentHendelse(
        id: HendelseId,
        sessionContext: SessionContext? = null,
    ): TilbakekrevingsbehandlingHendelse?

    fun hentForSak(sakId: UUID, sessionContext: SessionContext? = null): TilbakekrevingsbehandlingHendelser

    fun hentBehandlingsSerieFor(
        sakId: UUID,
        tilbakekrevingsbehandlingId: TilbakekrevingsbehandlingId,
        sessionContext: SessionContext? = null,
    ): TilbakekrevingbehandlingsSerie
}
