package no.nav.su.se.bakover.web.routes.tilbakekreving.kravgrunnlag

import arrow.core.merge
import arrow.core.raise.either
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
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
import tilbakekreving.application.service.kravgrunnlag.OppdaterKravgrunnlagService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.kravgrunnlag.KunneIkkeOppdatereKravgrunnlag
import tilbakekreving.domain.kravgrunnlag.OppdaterKravgrunnlagCommand
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toStringifiedJson

data class OppdaterKravgrunnlagRequest(val versjon: Long)

internal fun Route.oppdaterKravgrunnlagRoute(
    oppdaterKravgrunnlagService: OppdaterKravgrunnlagService,
) {
    val log = LoggerFactory.getLogger(this::class.java)
    post("$TILBAKEKREVING_PATH/{tilbakekrevingsId}/oppdaterKravgrunnlag") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withTilbakekrevingId { tilbakekrevingId ->
                    call.withValidertBody<OppdaterKravgrunnlagRequest>(log) { body ->
                        either {
                            val behandling = oppdaterKravgrunnlagService.oppdater(
                                command = OppdaterKravgrunnlagCommand(
                                    sakId = sakId,
                                    behandlingId = TilbakekrevingsbehandlingId(tilbakekrevingId),
                                    oppdatertAv = call.suUserContext.saksbehandler,
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

internal fun KunneIkkeOppdatereKravgrunnlag.tilResultat(): Resultat = when (this) {
    KunneIkkeOppdatereKravgrunnlag.FantIkkeUteståendeKravgrunnlag -> HttpStatusCode.BadRequest.errorJson(
        "Fant ikke utestående kravgrunnlag på sak",
        "fant_ikke_utestående_kravgrunnlag_på_sak",
    )
    is KunneIkkeOppdatereKravgrunnlag.IkkeTilgang -> ikkeTilgangTilSak
}
