package tilbakekreving.infrastructure

import tilbakekreving.domain.opprett.OpprettetTilbakekrevingsbehandlingHendelse

data class OpprettTilbakekrevingsbehandlingHendelseJson(
    val opprettetAv: String,
)

fun OpprettetTilbakekrevingsbehandlingHendelse.toJson() {
}
