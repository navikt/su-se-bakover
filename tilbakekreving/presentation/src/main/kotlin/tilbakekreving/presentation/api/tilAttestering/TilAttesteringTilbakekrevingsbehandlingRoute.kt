package tilbakekreving.presentation.api.tilAttestering

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.correlationId
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withTilbakekrevingId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.application.service.tilAttestering.TilbakekrevingsbehandlingTilAttesteringService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.tilAttestering.KunneIkkeSendeTilAttestering
import tilbakekreving.domain.tilAttestering.TilbakekrevingsbehandlingTilAttesteringCommand
import tilbakekreving.presentation.api.TILBAKEKREVING_PATH
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toStringifiedJson
import tilbakekreving.presentation.api.common.ikkeTilgangTilSak
import tilbakekreving.presentation.api.common.kravgrunnlagetHarEndretSeg

private data class Body(
    val saksversjon: Long,
)

internal fun Route.tilAttesteringTilbakekrevingsbehandlingRoute(
    service: TilbakekrevingsbehandlingTilAttesteringService,
) {
    post("$TILBAKEKREVING_PATH/{tilbakekrevingsId}/tilAttestering") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withTilbakekrevingId { id ->
                    call.withBody<Body> { body ->
                        service.tilAttestering(
                            command = TilbakekrevingsbehandlingTilAttesteringCommand(
                                sakId = sakId,
                                tilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId(id),
                                utførtAv = call.suUserContext.saksbehandler,
                                correlationId = call.correlationId,
                                brukerroller = call.suUserContext.roller.toNonEmptyList(),
                                klientensSisteSaksversjon = Hendelsesversjon(body.saksversjon),
                            ),
                        ).fold(
                            ifLeft = { call.svar(it.tilResultat()) },
                            ifRight = { call.svar(Resultat.json(HttpStatusCode.Created, it.toStringifiedJson())) },
                        )
                    }
                }
            }
        }
    }
}

private fun KunneIkkeSendeTilAttestering.tilResultat(): Resultat = when (this) {
    is KunneIkkeSendeTilAttestering.IkkeTilgang -> ikkeTilgangTilSak
    is KunneIkkeSendeTilAttestering.KravgrunnlagetHarEndretSeg -> kravgrunnlagetHarEndretSeg
    KunneIkkeSendeTilAttestering.UlikVersjon -> Feilresponser.utdatertVersjon
}
