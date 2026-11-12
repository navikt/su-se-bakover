package no.nav.su.se.bakover.web.routes.tilbakekreving.notat

import arrow.core.merge
import arrow.core.raise.either
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.NonBlankString.Companion.toNonBlankString
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.correlationId
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withTilbakekrevingId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.tilbakekreving.TILBAKEKREVING_PATH
import no.nav.su.se.bakover.web.routes.tilbakekreving.ikkeTilgangTilSak
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.notat.NotatTilbakekrevingsbehandlingService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.notat.KunneIkkeOppdatereNotat
import tilbakekreving.domain.notat.OppdaterNotatCommand
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toStringifiedJson

data class TkNotatRequest(
    val versjon: Long,
    val notat: String?,
)

internal fun Route.notatTilbakekrevingsbehandlingRoute(
    notatTilbakekrevingsbehandlingService: NotatTilbakekrevingsbehandlingService,
) {
    val log = LoggerFactory.getLogger(this::class.java)
    post("$TILBAKEKREVING_PATH/{tilbakekrevingsId}/notat") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withTilbakekrevingId { tilbakekrevingId ->
                    call.withValidertBody<TkNotatRequest>(log) { body ->
                        either {
                            val notat = try {
                                body.notat?.toNonBlankString()
                            } catch (_: IllegalArgumentException) {
                                log.info("Blankt notat mottat, avviser request")
                                raise(HttpStatusCode.BadRequest.errorJson(message = "Kan ikke være blankt notat", "notat_mangler_innhold"))
                            }
                            val behandling = notatTilbakekrevingsbehandlingService.lagreNotat(
                                command = OppdaterNotatCommand(
                                    sakId = sakId,
                                    correlationId = call.correlationId,
                                    brukerroller = call.suUserContext.roller,
                                    notat = notat,
                                    behandlingId = TilbakekrevingsbehandlingId(tilbakekrevingId),
                                    utførtAv = call.suUserContext.saksbehandler,
                                    klientensSisteSaksversjon = Hendelsesversjon(body.versjon),
                                ),
                            ).mapLeft { feil -> feil.tilResultat() }.bind()
                            Resultat.json(HttpStatusCode.Created, behandling.toStringifiedJson())
                        }.merge().let { resultat -> call.svar(resultat) }
                    }
                }
            }
        }
    }
}

internal fun KunneIkkeOppdatereNotat.tilResultat(): Resultat = when (this) {
    is KunneIkkeOppdatereNotat.IkkeTilgang -> ikkeTilgangTilSak
}
