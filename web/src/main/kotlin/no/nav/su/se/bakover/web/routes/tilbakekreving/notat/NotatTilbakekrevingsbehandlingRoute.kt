package no.nav.su.se.bakover.web.routes.tilbakekreving.notat

import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.NonBlankString.Companion.toNonBlankString
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.correlationId
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.log
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withTilbakekrevingId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.web.routes.tilbakekreving.TILBAKEKREVING_PATH
import no.nav.su.se.bakover.web.routes.tilbakekreving.ikkeTilgangTilSak
import tilbakekreving.application.service.notat.NotatTilbakekrevingsbehandlingService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.notat.KunneIkkeOppdatereNotat
import tilbakekreving.domain.notat.OppdaterNotatCommand
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toStringifiedJson

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
                        val notat = try {
                            body.notat?.toNonBlankString()
                        } catch (_: IllegalArgumentException) {
                            log.info("Blankt notat mottat, avviser request")
                            call.svar(HttpStatusCode.BadRequest.errorJson(message = "Kan ikke være blankt notat", "notat_mangler_innhold"))
                            return@withBody
                        }
                        notatTilbakekrevingsbehandlingService.lagreNotat(
                            command = OppdaterNotatCommand(
                                sakId = sakId,
                                correlationId = call.correlationId,
                                brukerroller = call.suUserContext.roller,
                                notat = notat,
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

internal fun KunneIkkeOppdatereNotat.tilResultat(): Resultat = when (this) {
    is KunneIkkeOppdatereNotat.IkkeTilgang -> ikkeTilgangTilSak
}
