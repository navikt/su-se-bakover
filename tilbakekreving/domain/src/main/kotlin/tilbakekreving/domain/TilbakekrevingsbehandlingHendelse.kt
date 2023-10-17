package tilbakekreving.domain

import no.nav.su.se.bakover.common.ident.NavIdentBruker
import no.nav.su.se.bakover.hendelse.domain.Sakshendelse

sealed interface TilbakekrevingsbehandlingHendelse : Sakshendelse {
    val id: TilbakekrevingsbehandlingId
    val utførtAv: NavIdentBruker

    /**
     * Tilstand + hendelse = ny tilstand
     * Den første hendelsen for en tilbakekrevingsbehandling er alltid [OpprettetTilbakekrevingsbehandling]
     */
    fun applyToState(behandling: Tilbakekrevingsbehandling): Tilbakekrevingsbehandling
}
