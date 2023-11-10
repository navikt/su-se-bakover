package tilbakekreving.presentation.api.kravgrunnlag

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.infrastructure.web.Resultat
import no.nav.su.se.bakover.common.infrastructure.web.authorize
import no.nav.su.se.bakover.common.infrastructure.web.correlationId
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withTilbakekrevingId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.application.service.kravgrunnlag.OppdaterKravgrunnlagService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.kravgrunnlag.KunneIkkeOppdatereKravgrunnlag
import tilbakekreving.domain.kravgrunnlag.OppdaterKravgrunnlagCommand
import tilbakekreving.presentation.api.TILBAKEKREVING_PATH
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toStringifiedJson

private data class Body(val versjon: Long)

internal fun Route.oppdaterKravgrunnlagRoute(
    oppdaterKravgrunnlagService: OppdaterKravgrunnlagService,
) {
    post("$TILBAKEKREVING_PATH/oppdaterKravgrunnlag") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withTilbakekrevingId { tilbakekrevingId ->
                    call.withBody<Body> { body ->
                        oppdaterKravgrunnlagService.oppdater(
                            command = OppdaterKravgrunnlagCommand(
                                sakId = sakId,
                                behandlingId = TilbakekrevingsbehandlingId(tilbakekrevingId),
                                oppdatertAv = call.suUserContext.saksbehandler,
                                correlationId = call.correlationId,
                                brukerroller = call.suUserContext.roller.toNonEmptyList(),
                                klientensSisteSaksversjon = Hendelsesversjon(body.versjon),
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

internal fun KunneIkkeOppdatereKravgrunnlag.tilResultat(): Resultat {
    TODO()
}
