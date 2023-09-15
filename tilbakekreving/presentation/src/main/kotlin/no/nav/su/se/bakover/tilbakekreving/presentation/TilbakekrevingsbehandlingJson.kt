package no.nav.su.se.bakover.tilbakekreving.presentation

import no.nav.su.se.bakover.common.serialize
import no.nav.su.se.bakover.tilbakekreving.domain.Tilbakekrevingsbehandling

class TilbakekrevingsbehandlingJson {

    companion object {
        fun Tilbakekrevingsbehandling.toJson(): String {
            return serialize(TilbakekrevingsbehandlingJson())
        }
    }
}
