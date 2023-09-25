package tilbakekreving.infrastructure

import tilbakekreving.domain.opprett.OpprettetTilbakekrevingsbehandlingHendelse

internal data class OpprettTilbakekrevingsbehandlingHendelseDbJson(
    val opprettetAv: String,
)

internal fun OpprettetTilbakekrevingsbehandlingHendelse.toJson() {
}
