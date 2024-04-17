package no.nav.su.se.bakover.web.routes.revurdering

import no.nav.su.se.bakover.domain.revurdering.tilbakekreving.HistoriskSendtTilbakekrevingsvedtak

data class TilbakekrevingsbehandlingJson(
    val avgjørelse: TilbakekrevingsAvgjørelseJson,
) {
    enum class TilbakekrevingsAvgjørelseJson {
        TILBAKEKREV,
        IKKE_TILBAKEKREV,
    }
}

fun HistoriskSendtTilbakekrevingsvedtak?.toJson(): TilbakekrevingsbehandlingJson? {
    return this?.avgjørelse?.let {
        when (it) {
            HistoriskSendtTilbakekrevingsvedtak.AvgjørelseTilbakekrevingUnderRevurdering.Tilbakekrev -> TilbakekrevingsbehandlingJson(
                TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV,
            )

            HistoriskSendtTilbakekrevingsvedtak.AvgjørelseTilbakekrevingUnderRevurdering.IkkeTilbakekrev -> TilbakekrevingsbehandlingJson(
                TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.IKKE_TILBAKEKREV,
            )
        }
    }
}
