package tilbakekreving.domain

import java.util.UUID

/**
 * En samling av alle tilbakekrevingsbehandlinger for en sak.
 * @see [no.nav.su.se.bakover.utenlandsopphold.domain.RegistrerteUtenlandsopphold]
 */
data class Tilbakekrevingsbehandlinger(
    val sakId: UUID,
    val behandlinger: List<Tilbakekrevingsbehandling>,
) : List<Tilbakekrevingsbehandling> by behandlinger {
    init {
        this.map { it.versjon }.let {
            require(it.sorted() == it) {
                "Tilbakekrevingsbehandlinger for sak $sakId må være sortert etter versjon, men var: $it"
            }
            require(it.distinct() == it) {
                "Tilbakekrevingsbehandlinger for sak $sakId kan ikke inneholde duplikater: $it"
            }
        }
    }

    companion object {
        fun empty(sakId: UUID) = Tilbakekrevingsbehandlinger(sakId, listOf())
    }
}
