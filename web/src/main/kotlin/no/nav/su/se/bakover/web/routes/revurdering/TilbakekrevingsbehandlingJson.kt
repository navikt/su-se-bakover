package no.nav.su.se.bakover.web.routes.revurdering

import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.AvventerKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeTilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.MottattKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.SendtTilbakekrevingsvedtak
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrev
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Tilbakekrevingsbehandling

data class TilbakekrevingsbehandlingJson(
    val avgjørelse: TilbakekrevingsAvgjørelseJson,
) {
    enum class TilbakekrevingsAvgjørelseJson {
        IKKE_AVGJORT,
        TILBAKEKREV,
        IKKE_TILBAKEKREV,
    }
}

fun Tilbakekrevingsbehandling.toJson(): TilbakekrevingsbehandlingJson? {
    return when (this) {
        is AvventerKravgrunnlag -> {
            toJson()
        }
        is Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving -> {
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
        is Tilbakekrevingsbehandling.UnderBehandling.IkkeBehovForTilbakekreving -> {
            toJson()
        }
        is SendtTilbakekrevingsvedtak -> {
            toJson()
        }
    }
}

fun Tilbakekrevingsbehandling.Ferdigbehandlet.toJson(): TilbakekrevingsbehandlingJson? {
    return when (this) {
        is AvventerKravgrunnlag -> {
            this.avgjort.toJson()
        }
        is Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving -> {
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

fun Tilbakekrevingsbehandling.UnderBehandling.toJson(): TilbakekrevingsbehandlingJson? {
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
        is Tilbakekrevingsbehandling.UnderBehandling.IkkeBehovForTilbakekreving -> {
            null
        }
    }
}

fun Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.Avgjort.toJson(): TilbakekrevingsbehandlingJson {
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

fun Tilbakekrevingsbehandling.UnderBehandling.VurderTilbakekreving.IkkeAvgjort.toJson(): TilbakekrevingsbehandlingJson {
    return when (this) {
        is IkkeAvgjort -> {
            TilbakekrevingsbehandlingJson(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.IKKE_AVGJORT,
            )
        }
    }
}
