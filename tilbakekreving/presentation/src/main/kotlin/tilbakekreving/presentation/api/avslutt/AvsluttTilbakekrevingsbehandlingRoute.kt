package tilbakekreving.presentation.api.avslutt

import arrow.core.Either
import arrow.core.right
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.su.se.bakover.common.CorrelationId
import no.nav.su.se.bakover.common.brukerrolle.Brukerrolle
import no.nav.su.se.bakover.common.domain.extensions.toNonEmptyList
import no.nav.su.se.bakover.common.ident.NavIdentBruker
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
import tilbakekreving.application.service.avbrutt.AvbrytTilbakekrevingsbehandlingService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.avbrudd.AvbrytTilbakekrevingsbehandlingCommand
import tilbakekreving.domain.avbrudd.KunneIkkeAvbryte
import tilbakekreving.presentation.api.TILBAKEKREVING_PATH
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toStringifiedJson
import tilbakekreving.presentation.api.common.ikkeTilgangTilSak
import java.util.UUID

private data class Body(
    val versjon: Long,
    val begrunnelse: String,
) {
    fun toCommand(
        sakId: UUID,
        behandlingsId: UUID,
        utførtAv: NavIdentBruker.Saksbehandler,
        correlationId: CorrelationId,
        brukerroller: List<Brukerrolle>,
    ): Either<Resultat, AvbrytTilbakekrevingsbehandlingCommand> {
        return AvbrytTilbakekrevingsbehandlingCommand(
            sakId = sakId,
            behandlingsId = TilbakekrevingsbehandlingId(behandlingsId),
            utførtAv = utførtAv,
            correlationId = correlationId,
            brukerroller = brukerroller.toNonEmptyList(),
            klientensSisteSaksversjon = Hendelsesversjon(versjon),
            begrunnelse = begrunnelse,
        ).right()
    }
}

internal fun Route.avbrytTilbakekrevingsbehandlingRoute(
    service: AvbrytTilbakekrevingsbehandlingService,
) {
    post("$TILBAKEKREVING_PATH/{tilbakekrevingsId}/avbryt") {
        authorize(Brukerrolle.Saksbehandler, Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withTilbakekrevingId { behandlingsId ->
                    call.withBody<Body> { body ->
                        service.avbryt(
                            AvbrytTilbakekrevingsbehandlingCommand(
                                sakId = sakId,
                                behandlingsId = TilbakekrevingsbehandlingId(behandlingsId),
                                utførtAv = call.suUserContext.saksbehandler,
                                correlationId = call.correlationId,
                                brukerroller = call.suUserContext.roller.toNonEmptyList(),
                                klientensSisteSaksversjon = Hendelsesversjon(body.versjon),
                                begrunnelse = body.begrunnelse,
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

private fun KunneIkkeAvbryte.tilResultat(): Resultat = when (this) {
    is KunneIkkeAvbryte.IkkeTilgang -> ikkeTilgangTilSak
    KunneIkkeAvbryte.UlikVersjon -> Feilresponser.utdatertVersjon
}
