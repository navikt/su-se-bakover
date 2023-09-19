package tilbakekreving.presentation.api.opprett

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import tilbakekreving.application.service.OpprettTilbakekrevingsbehandlingService
import tilbakekreving.domain.opprett.KunneIkkeOppretteTilbakekrevingsbehandling
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toJson
import tilbakekreving.presentation.api.common.ikkeTilgangTilSak
import tilbakekreving.presentation.api.common.ingenÅpneKravgrunnlag
import tilbakekreving.presentation.api.tilbakekrevingPath
import tilbakekreving.presentation.consumer.TilbakekrevingsmeldingMapper

internal fun Route.opprettTilbakekrevingsbehandlingRoute(
    opprettTilbakekrevingsbehandlingService: OpprettTilbakekrevingsbehandlingService,
) {
    post("$tilbakekrevingPath/ny") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId {
                opprettTilbakekrevingsbehandlingService.opprett(
                    sakId = it,
                    kravgrunnlagMapper = TilbakekrevingsmeldingMapper::toKravgrunnlag,
                ).fold(
                    ifLeft = { call.svar(it.tilResultat()) },
                    ifRight = { call.svar(Resultat.json(HttpStatusCode.Created, it.toJson())) },
                )
            }
        }
    }
}

private fun KunneIkkeOppretteTilbakekrevingsbehandling.tilResultat(): Resultat = when (this) {
    KunneIkkeOppretteTilbakekrevingsbehandling.IngenÅpneKravgrunnlag -> ingenÅpneKravgrunnlag
    is KunneIkkeOppretteTilbakekrevingsbehandling.IkkeTilgang -> ikkeTilgangTilSak
}
