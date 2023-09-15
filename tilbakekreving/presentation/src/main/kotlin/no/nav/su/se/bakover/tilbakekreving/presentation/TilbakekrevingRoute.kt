package no.nav.su.se.bakover.tilbakekreving.presentation

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.tilbakekreving.application.service.KunneIkkeOppretteTilbakekrevingsbehandling
import no.nav.su.se.bakover.tilbakekreving.application.service.TilbakekrevingService
import no.nav.su.se.bakover.tilbakekreving.presentation.TilbakekrevingsbehandlingJson.Companion.toJson

internal const val tilbakekrevingPath = "saker/{sakId}/tilbakekreving"

internal fun Route.tilbakekrevingRoute(tilbakekrevingService: TilbakekrevingService) {
    post("$tilbakekrevingPath/ny") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId {
                tilbakekrevingService.ny(it).fold(
                    ifLeft = { call.svar(it.tilResultat()) },
                    ifRight = { call.svar(Resultat.json(HttpStatusCode.Created, it.toJson())) },
                )
            }
        }
    }
}

internal fun KunneIkkeOppretteTilbakekrevingsbehandling.tilResultat(): Resultat {
    TODO()
}
