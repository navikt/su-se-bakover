package tilbakekreving.presentation.api.forhåndsvarsel

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.parameter
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withTilbakekrevingId
import tilbakekreving.application.service.forhåndsvarsel.VisUtsendtForhåndsvarselbrevForTilbakekrevingService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.forhåndsvarsel.KunneIkkeHenteUtsendtForhåndsvarsel
import tilbakekreving.domain.forhåndsvarsel.VisUtsendtForhåndsvarselbrevCommand
import tilbakekreving.presentation.api.TILBAKEKREVING_PATH
import java.util.UUID

internal fun Route.visUtsendtForhåndsvarselbrevForTilbakekrevingRoute(
    service: VisUtsendtForhåndsvarselbrevForTilbakekrevingService,
) {
    get("$TILBAKEKREVING_PATH/{tilbakekrevingsId}/forhandsvarsel/{dokumentId}") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withTilbakekrevingId { tilbakekrevingId ->
                    call.parameter("dokumentId").fold(
                        { call.svar(it) },
                        {
                            service.hent(
                                VisUtsendtForhåndsvarselbrevCommand(sakId, TilbakekrevingsbehandlingId(tilbakekrevingId), UUID.fromString(it)),
                            ).fold(
                                { call.svar(it.toResultat()) },
                                { call.respondBytes(it.getContent(), ContentType.Application.Pdf) },
                            )
                        },
                    )
                }
            }
        }
    }
}

internal fun KunneIkkeHenteUtsendtForhåndsvarsel.toResultat(): Resultat {
    return when (this) {
        KunneIkkeHenteUtsendtForhåndsvarsel.FantIkkeDokument -> HttpStatusCode.InternalServerError.errorJson(
            "Fant ikke forhåndsvarsel dokumentet",
            "fant_ikke_forhåndsvarsel_dokument",
        )
    }
}
