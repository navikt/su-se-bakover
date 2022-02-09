package no.nav.su.se.bakover.web.routes.revurdering

import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.AvventerKravgrunnlag
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.BurdeForstått
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.Forsto
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.IkkeAvgjort
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.KravgrunnlagBesvart
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.KunneIkkeForstå
import no.nav.su.se.bakover.domain.oppdrag.tilbakekreving.MottattKravgrunnlag
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
        is AvventerKravgrunnlag -> {
            toJson()
        }
        is Tilbakekrevingsbehandling.Ferdigbehandlet.UtenKravgrunnlag.IkkeBehovForTilbakekreving -> {
            toJson()
        }
        is MottattKravgrunnlag -> {
            toJson()
        }
        is BurdeForstått -> {
            toJson()
        }
        is Forsto -> {
            toJson()
        }
        is KunneIkkeForstå -> {
            toJson()
        }
        is IkkeAvgjort -> {
            toJson()
        }
        is Tilbakekrevingsbehandling.UnderBehandling.IkkeBehovForTilbakekreving -> {
            toJson()
        }
        is KravgrunnlagBesvart -> {
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
        is KravgrunnlagBesvart -> {
            this.avgjort.toJson()
        }
    }
}

fun Tilbakekrevingsbehandling.UnderBehandling.toJson(): TilbakekrevingsbehandlingJson? {
    return when (this) {
        is BurdeForstått -> {
            toJson()
        }
        is Forsto -> {
            toJson()
        }
        is KunneIkkeForstå -> {
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
        is BurdeForstått -> {
            TilbakekrevingsbehandlingJson(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.BURDE_FORSTÅTT,
            )
        }
        is Forsto -> {
            TilbakekrevingsbehandlingJson(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.FORSTO,
            )
        }
        is KunneIkkeForstå -> {
            TilbakekrevingsbehandlingJson(
                avgjørelse = TilbakekrevingsbehandlingJson.TilbakekrevingsAvgjørelseJson.KUNNE_IKKE_FORSTÅ,
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
