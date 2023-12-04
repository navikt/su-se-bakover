package tilbakekreving.presentation.api.notat

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.NonBlankString.Companion.toNonBlankString
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.correlationId
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withTilbakekrevingId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.application.service.notat.NotatTilbakekrevingsbehandlingService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.notat.KunneIkkeLagreNotat
import tilbakekreving.domain.notat.OppdaterNotatCommand
import tilbakekreving.presentation.api.TILBAKEKREVING_PATH
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toStringifiedJson
import tilbakekreving.presentation.api.common.ikkeTilgangTilSak

private data class Body(
    val versjon: Long,
    val notat: String?,
)

internal fun Route.notatTilbakekrevingsbehandlingRoute(
    notatTilbakekrevingsbehandlingService: NotatTilbakekrevingsbehandlingService,
) {
    post("$TILBAKEKREVING_PATH/{tilbakekrevingsId}/notat") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withTilbakekrevingId { tilbakekrevingId ->
                    call.withBody<Body> { body ->
                        notatTilbakekrevingsbehandlingService.lagreNotat(
                            command = OppdaterNotatCommand(
                                sakId = sakId,
                                correlationId = call.correlationId,
                                brukerroller = call.suUserContext.roller,
                                notat = body.notat?.toNonBlankString(),
                                behandlingId = TilbakekrevingsbehandlingId(tilbakekrevingId),
                                utførtAv = call.suUserContext.saksbehandler,
                                klientensSisteSaksversjon = Hendelsesversjon(body.versjon),
                            ),
                        ).fold(
                            { call.svar(it.tilResultat()) },
                            { call.svar(Resultat.json(HttpStatusCode.Created, it.toStringifiedJson())) },
                        )
                    }
                }
            }
        }
    }
}

internal fun KunneIkkeLagreNotat.tilResultat(): Resultat = when (this) {
    is KunneIkkeLagreNotat.IkkeTilgang -> ikkeTilgangTilSak
}
