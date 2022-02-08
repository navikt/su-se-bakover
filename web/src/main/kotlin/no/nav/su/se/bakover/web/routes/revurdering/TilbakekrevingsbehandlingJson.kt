package no.nav.su.se.bakover.web.routes.revurdering

import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.FullstendigTilbakekrevingsbehandling
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling

data class TilbakekrevingsbehandlingJson(
    val avgjørelse: TilbakekrevingsAvgjørelseJson,
) {
    enum class TilbakekrevingsAvgjørelseJson {
        IKKE_AVGJORT,
        FORSTO,
        BURDE_FORSTÅTT,
        KUNNE_IKKE_FORSTÅ
    }
}

fun Tilbakekrevingsbehandling.toJson(): TilbakekrevingsbehandlingJson? {
    return when (this) {
        is Tilbakekrevingsbehandling.IkkeBehovForTilbakekreving -> {
            null
        }
        is Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort.BurdeForstått -> {
            TilbakekrevingsbehandlingJson(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.BURDE_FORSTÅTT,
            )
        }
        is Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort.Forsto -> {
            TilbakekrevingsbehandlingJson(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.FORSTO,
            )
        }
        is Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort.KunneIkkeForstått -> {
            TilbakekrevingsbehandlingJson(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.KUNNE_IKKE_FORSTÅ,
            )
        }
        is Tilbakekrevingsbehandling.VurderTilbakekreving.IkkeAvgjort -> {
            TilbakekrevingsbehandlingJson(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.IKKE_AVGJORT,
            )
        }
    }
}

fun FullstendigTilbakekrevingsbehandling.fullstendigJson(): TilbakekrevingsbehandlingJson? {
    return when (this) {
        is Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort.BurdeForstått -> {
            this.toJson()
        }
        is Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort.Forsto -> {
            this.toJson()
        }
        is Tilbakekrevingsbehandling.VurderTilbakekreving.Avgjort.KunneIkkeForstått -> {
            this.toJson()
        }
        is Tilbakekrevingsbehandling.IkkeBehovForTilbakekreving -> {
            this.toJson()
        }
    }
}
