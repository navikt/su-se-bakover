package no.nav.su.se.bakover.web.routes.tilbakekreving.tilAttestering

import arrow.core.merge
import arrow.core.raise.either
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.web.Feilresponser
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.correlationId
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withTilbakekrevingId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import no.nav.su.se.bakover.web.inputvalidation.withValidertBody
import no.nav.su.se.bakover.web.routes.tilbakekreving.TILBAKEKREVING_PATH
import no.nav.su.se.bakover.web.routes.tilbakekreving.ikkeTilgangTilSak
import no.nav.su.se.bakover.web.routes.tilbakekreving.kravgrunnlagetHarEndretSeg
import org.slf4j.LoggerFactory
import tilbakekreving.application.service.tilAttestering.TilbakekrevingsbehandlingTilAttesteringService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.tilAttestering.KunneIkkeSendeTilAttestering
import tilbakekreving.domain.tilAttestering.TilbakekrevingsbehandlingTilAttesteringCommand
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toStringifiedJson

data class TilbakekrevingTilAttesteringRequest(
    val versjon: Long,
)

internal fun Route.tilAttesteringTilbakekrevingsbehandlingRoute(
    service: TilbakekrevingsbehandlingTilAttesteringService,
) {
    val log = LoggerFactory.getLogger(this::class.java)
    post("$TILBAKEKREVING_PATH/{tilbakekrevingsId}/tilAttestering") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withTilbakekrevingId { id ->
                    call.withValidertBody<TilbakekrevingTilAttesteringRequest>(log) { body ->
                        either {
                            val behandling = service.tilAttestering(
                                command = TilbakekrevingsbehandlingTilAttesteringCommand(
                                    sakId = sakId,
                                    tilbakekrevingsbehandlingId = TilbakekrevingsbehandlingId(id),
                                    utførtAv = call.suUserContext.saksbehandler,
                                    correlationId = call.correlationId,
                                    brukerroller = call.suUserContext.roller.toNonEmptyList(),
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

private fun KunneIkkeSendeTilAttestering.tilResultat(): Resultat = when (this) {
    is KunneIkkeSendeTilAttestering.IkkeTilgang -> ikkeTilgangTilSak
    is KunneIkkeSendeTilAttestering.KravgrunnlagetHarEndretSeg -> kravgrunnlagetHarEndretSeg
    KunneIkkeSendeTilAttestering.UlikVersjon -> Feilresponser.utdatertVersjon
}
