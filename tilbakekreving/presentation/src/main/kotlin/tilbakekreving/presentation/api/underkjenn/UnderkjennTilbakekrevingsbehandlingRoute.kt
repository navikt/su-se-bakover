package tilbakekreving.presentation.api.underkjenn

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.left
import arrow.core.right
import io.ktor.http.HttpStatusCode
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
import no.nav.su.se.bakover.common.infrastructure.web.errorJson
import no.nav.su.se.bakover.common.infrastructure.web.suUserContext
import no.nav.su.se.bakover.common.infrastructure.web.svar
import no.nav.su.se.bakover.common.infrastructure.web.withBody
import no.nav.su.se.bakover.common.infrastructure.web.withSakId
import no.nav.su.se.bakover.common.infrastructure.web.withTilbakekrevingId
import no.nav.su.se.bakover.hendelse.domain.Hendelsesversjon
import tilbakekreving.application.service.underkjenn.UnderkjennTilbakekrevingsbehandlingService
import tilbakekreving.domain.TilbakekrevingsbehandlingId
import tilbakekreving.domain.underkjennelse.KunneIkkeUnderkjenne
import tilbakekreving.domain.underkjennelse.UnderkjennAttesteringsgrunnTilbakekreving
import tilbakekreving.domain.underkjennelse.UnderkjennTilbakekrevingsbehandlingCommand
import tilbakekreving.presentation.api.TILBAKEKREVING_PATH
import tilbakekreving.presentation.api.common.TilbakekrevingsbehandlingJson.Companion.toStringifiedJson
import tilbakekreving.presentation.api.common.ikkeTilgangTilSak
import java.util.UUID

private data class Body(
    val versjon: Long,
    val kommentar: String,
    val grunn: String,
) {
    fun toCommand(
        sakId: UUID,
        behandlingsId: UUID,
        utførtAv: NavIdentBruker.Attestant,
        correlationId: CorrelationId,
        brukerroller: List<Brukerrolle>,
    ): Either<Resultat, UnderkjennTilbakekrevingsbehandlingCommand> {
        return UnderkjennTilbakekrevingsbehandlingCommand(
            sakId = sakId,
            behandlingsId = TilbakekrevingsbehandlingId(behandlingsId),
            utførtAv = utførtAv,
            correlationId = correlationId,
            brukerroller = brukerroller.toNonEmptyList(),
            klientensSisteSaksversjon = Hendelsesversjon(versjon),
            grunn = Either.catch {
                UnderkjennAttesteringsgrunnTilbakekreving.valueOf(grunn)
            }.getOrElse {
                return HttpStatusCode.BadRequest.errorJson(
                    "Kunne ikke mappe grunn til Attesteringsgrunn - input var $grunn",
                    "kunne_ikke_mappe_attesteringsgrunn",
                ).left()
            },
            kommentar = kommentar,
        ).right()
    }
}

internal fun Route.underkjennTilbakekrevingsbehandlingRoute(
    service: UnderkjennTilbakekrevingsbehandlingService,
) {
    post("$TILBAKEKREVING_PATH/{tilbakekrevingsId}/underkjenn") {
        authorize(Brukerrolle.Attestant) {
            call.withSakId { sakId ->
                call.withTilbakekrevingId { id ->
                    call.withBody<Body> { body ->
                        body.toCommand(
                            sakId = sakId,
                            behandlingsId = id,
                            utførtAv = call.suUserContext.attestant,
                            correlationId = call.correlationId,
                            brukerroller = call.suUserContext.roller,
                        ).fold(
                            { call.svar(it) },
                            {
                                service.underkjenn(it).fold(
                                    { call.svar(it.tilResultat()) },
                                    { call.svar(Resultat.json(HttpStatusCode.Created, it.toStringifiedJson())) },
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun KunneIkkeUnderkjenne.tilResultat(): Resultat = when (this) {
    is KunneIkkeUnderkjenne.IkkeTilgang -> ikkeTilgangTilSak
    KunneIkkeUnderkjenne.UlikVersjon -> Feilresponser.utdatertVersjon
}
