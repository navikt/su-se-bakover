package no.nav.su.se.bakover.web.routes.revurdering

import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.AvventerKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.IkkeTilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.MottattKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.SendtTilbakekrevingsvedtak
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.Tilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.tilbakekrevingUnderRevurdering.TilbakekrevingsbehandlingUnderRevurdering

data class TilbakekrevingsbehandlingJson(
    val avgjørelse: TilbakekrevingsAvgjørelseJson,
) {
    enum class TilbakekrevingsAvgjørelseJson {
        IKKE_AVGJORT,
        TILBAKEKREV,
        IKKE_TILBAKEKREV,
    }
}

fun TilbakekrevingsbehandlingUnderRevurdering.toJson(): TilbakekrevingsbehandlingJson? {
    return when (this) {
        is AvventerKravgrunnlag -> {
            toJson()
        }
        is TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving -> {
            toJson()
        }
        is MottattKravgrunnlag -> {
            toJson()
        }
        is Tilbakekrev -> {
            toJson()
        }
        is IkkeTilbakekrev -> {
            toJson()
        }
        is IkkeAvgjort -> {
            toJson()
        }
        is TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.IkkeBehovForTilbakekreving -> {
            toJson()
        }
        is SendtTilbakekrevingsvedtak -> {
            toJson()
        }
    }
}

fun TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.toJson(): TilbakekrevingsbehandlingJson? {
    return when (this) {
        is AvventerKravgrunnlag -> {
            this.avgjort.toJson()
        }
        is TilbakekrevingsbehandlingUnderRevurdering.Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving -> {
            null
        }
        is MottattKravgrunnlag -> {
            this.avgjort.toJson()
        }
        is SendtTilbakekrevingsvedtak -> {
            this.avgjort.toJson()
        }
    }
}

fun TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.toJson(): TilbakekrevingsbehandlingJson? {
    return when (this) {
        is Tilbakekrev -> {
            toJson()
        }
        is IkkeTilbakekrev -> {
            toJson()
        }
        is IkkeAvgjort -> {
            toJson()
        }
        is TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.IkkeBehovForTilbakekreving -> {
            null
        }
    }
}

fun TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.VurderTilbakekreving.Avgjort.toJson(): TilbakekrevingsbehandlingJson {
    return when (this) {
        is Tilbakekrev -> {
            TilbakekrevingsbehandlingJson(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.TILBAKEKREV,
            )
        }
        is IkkeTilbakekrev -> {
            TilbakekrevingsbehandlingJson(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.IKKE_TILBAKEKREV,
            )
        }
    }
}

fun TilbakekrevingsbehandlingUnderRevurdering.UnderBehandling.VurderTilbakekreving.IkkeAvgjort.toJson(): TilbakekrevingsbehandlingJson {
    return when (this) {
        is IkkeAvgjort -> {
            TilbakekrevingsbehandlingJson(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.IKKE_AVGJORT,
            )
        }
    }
}
