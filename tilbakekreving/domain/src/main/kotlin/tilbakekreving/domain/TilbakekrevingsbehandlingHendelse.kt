package tilbakekreving.domain

import no.nav.su.se.bakover.hendelse.domain.Sakshendelse

sealed interface TilbakekrevingsbehandlingHendelse : Sakshendelse {
    val id: TilbakekrevingsbehandlingId
}
